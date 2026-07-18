package com.beachape.aminam.infra.authz.services;

import static com.beachape.aminam.domain.authz.models.ResourcePattern.wildcard;
import static com.beachape.aminam.domain.authz.models.ResourceType.DATABASE;
import static com.beachape.aminam.domain.authz.models.Verb.UPDATE;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.beachape.aminam.domain.authz.models.Action;
import com.beachape.aminam.domain.authz.models.Effect;
import com.beachape.aminam.domain.authz.models.PolicyDocument;
import com.beachape.aminam.domain.authz.models.Statement;
import com.beachape.aminam.domain.authz.services.PolicyValidator.InvalidPolicyException;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

final class CelPolicyValidatorTest {

  private final CelPolicyValidator validator = new CelPolicyValidator();

  @Test
  void acceptsAStatementWithAValidBooleanCondition() {
    assertThatCode(() -> validator.validate(document("resource.name.startsWith('report-')")))
        .doesNotThrowAnyException();
  }

  @Test
  void acceptsANullCondition() {
    assertThatCode(() -> validator.validate(document(null))).doesNotThrowAnyException();
  }

  @Test
  void rejectsAnEmptyDocument() {
    assertThatExceptionOfType(InvalidPolicyException.class)
        .isThrownBy(() -> validator.validate(new PolicyDocument(List.of())))
        .satisfies(e -> assertThat(locations(e)).contains("statements"));
  }

  @Test
  void rejectsEmptyActionsAndResources() {
    var document =
        new PolicyDocument(
            List.of(new Statement(Effect.ALLOW, Set.of(), Set.of(), Set.of(), null)));

    assertThatExceptionOfType(InvalidPolicyException.class)
        .isThrownBy(() -> validator.validate(document))
        .satisfies(
            e ->
                assertThat(locations(e))
                    .contains("statements[0].actions", "statements[0].resources"));
  }

  @Test
  void acceptsNonEmptyMembershipsForAResourcePolicy() {
    // A resource (sharing) policy names the memberships it trusts; the validator is point-agnostic,
    // so it accepts memberships and the engine reads them only in resource position.
    var document =
        new PolicyDocument(
            List.of(
                new Statement(
                    Effect.ALLOW,
                    Set.of(new MembershipId(randomUUID())),
                    Set.of(new Action(DATABASE, UPDATE)),
                    Set.of(wildcard(DATABASE)),
                    null)));

    assertThatCode(() -> validator.validate(document)).doesNotThrowAnyException();
  }

  @Test
  void rejectsAConditionThatDoesNotTypeCheckToBoolean() {
    assertThatExceptionOfType(InvalidPolicyException.class)
        .isThrownBy(() -> validator.validate(document("resource.name")))
        .satisfies(e -> assertThat(locations(e)).contains("statements[0].condition"));
  }

  @Test
  void rejectsAConditionReferencingAnUnknownRootObject() {
    // principal/resource are the only declared roots; an unknown root fails to type-check. (An
    // unknown KEY under a root is allowed by the open map and just evaluates false at runtime.)
    assertThatExceptionOfType(InvalidPolicyException.class)
        .isThrownBy(() -> validator.validate(document("mystery.x == 'y'")))
        .satisfies(e -> assertThat(locations(e)).contains("statements[0].condition"));
  }

  @Test
  void rejectsAMalformedCondition() {
    assertThatExceptionOfType(InvalidPolicyException.class)
        .isThrownBy(() -> validator.validate(document("resource.name ==")))
        .satisfies(e -> assertThat(locations(e)).contains("statements[0].condition"));
  }

  private static List<String> locations(InvalidPolicyException e) {
    return e.failures().stream().map(InvalidPolicyException.Failure::location).toList();
  }

  private static PolicyDocument document(@Nullable String condition) {
    return new PolicyDocument(
        List.of(
            new Statement(
                Effect.ALLOW,
                Set.of(),
                Set.of(new Action(DATABASE, UPDATE)),
                Set.of(wildcard(DATABASE)),
                condition)));
  }
}
