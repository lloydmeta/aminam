package com.beachape.aminam.infra.authz.services;

import static com.beachape.aminam.domain.authz.models.ResourceType.DATABASE;
import static com.beachape.aminam.domain.authz.models.Verb.CREATE;
import static com.beachape.aminam.domain.authz.models.Verb.UPDATE;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.authz.models.Action;
import com.beachape.aminam.domain.authz.models.AuthzPrincipal;
import com.beachape.aminam.domain.authz.models.EvaluationContext;
import com.beachape.aminam.domain.authz.models.ResourceFacts;
import com.beachape.aminam.domain.authz.models.ResourceRef;
import com.beachape.aminam.domain.orgs.models.Membership;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class CelConditionEvaluatorTest {

  private static final UserId USER = new UserId(randomUUID());
  private static final OrgId ORG = new OrgId(randomUUID());
  private static final UUID DB = randomUUID();

  private final CelConditionEvaluator evaluator = new CelConditionEvaluator();

  // --- resource.name (string functions) ---

  @Test
  void startsWithIsTrueWhenItMatches() {
    assertThat(evaluator.satisfied("resource.name.startsWith('report-')", db("report-sales")))
        .isTrue();
  }

  @Test
  void startsWithIsFalseWhenItDoesNotMatch() {
    assertThat(evaluator.satisfied("resource.name.startsWith('report-')", db("ledger"))).isFalse();
  }

  @Test
  void endsWithContainsAndSizeReadResourceName() {
    assertThat(evaluator.satisfied("resource.name.endsWith('-prod')", db("db-prod"))).isTrue();
    assertThat(evaluator.satisfied("resource.name.contains('mid')", db("a-mid-b"))).isTrue();
    assertThat(evaluator.satisfied("size(resource.name) <= 4", db("abcd"))).isTrue();
    assertThat(evaluator.satisfied("size(resource.name) <= 4", db("abcde"))).isFalse();
  }

  @Test
  void matchesAppliesARegex() {
    assertThat(evaluator.satisfied("resource.name.matches('^[a-z0-9-]+$')", db("ok-1"))).isTrue();
    assertThat(evaluator.satisfied("resource.name.matches('^[a-z0-9-]+$')", db("No Good")))
        .isFalse();
  }

  // --- server-resolved ids ---

  @Test
  void readsResourceTypeAndOrgId() {
    assertThat(evaluator.satisfied("resource.type == 'DATABASE'", db("x"))).isTrue();
    assertThat(evaluator.satisfied("resource.org_id == '" + ORG.value() + "'", db("x"))).isTrue();
  }

  @Test
  void readsResourceId() {
    assertThat(evaluator.satisfied("resource.id == '" + DB + "'", db("x"))).isTrue();
  }

  @Test
  void readsPrincipalIdAndActiveOrg() {
    assertThat(evaluator.satisfied("principal.id == '" + USER.value() + "'", db("x"))).isTrue();
    assertThat(evaluator.satisfied("resource.org_id == principal.active_org", db("x"))).isTrue();
  }

  @Test
  void readsPrincipalMembershipId() {
    var membership = new MembershipId(randomUUID());
    var ctx =
        new EvaluationContext(
            new AuthzPrincipal(USER, new Membership.UserMembership(membership, ORG)),
            new Action(DATABASE, UPDATE),
            new ResourceRef.Existing(DATABASE, DB),
            new ResourceFacts(ORG, Map.of("name", "x")),
            List.of(),
            List.of());
    assertThat(evaluator.satisfied("principal.membership_id == '" + membership.value() + "'", ctx))
        .isTrue();
  }

  // --- resource.created_by (ownership) ---

  @Test
  void ownershipHoldsForTheCreator() {
    assertThat(evaluator.satisfied("resource.created_by == principal.id", ownedBy(USER))).isTrue();
  }

  @Test
  void ownershipDoesNotHoldForAnotherUser() {
    assertThat(
            evaluator.satisfied(
                "resource.created_by == principal.id", ownedBy(new UserId(randomUUID()))))
        .isFalse();
  }

  @Test
  void aToCreateHasNoCreatedByAndFailsClosed() {
    // Why an owner-only policy needs a separate, unconditioned create statement: a ToCreate carries
    // no facts, so an ownership condition can never hold on it.
    var ctx =
        new EvaluationContext(
            new AuthzPrincipal(
                USER, new Membership.UserMembership(new MembershipId(randomUUID()), ORG)),
            new Action(DATABASE, CREATE),
            new ResourceRef.ToCreate(DATABASE, ORG),
            new ResourceFacts(ORG, Map.of()),
            List.of(),
            List.of());
    assertThat(evaluator.satisfied("resource.created_by == principal.id", ctx)).isFalse();
  }

  // --- operator families ---

  @Test
  void logicalAndComparisonOperators() {
    assertThat(evaluator.satisfied("resource.type == 'DATABASE' && resource.name != 'x'", db("y")))
        .isTrue();
    assertThat(evaluator.satisfied("resource.name == 'a' || resource.name == 'b'", db("b")))
        .isTrue();
    assertThat(evaluator.satisfied("!(resource.name == 'a')", db("b"))).isTrue();
    assertThat(evaluator.satisfied("resource.name in ['a', 'b']", db("b"))).isTrue();
    assertThat(evaluator.satisfied("resource.name == 'a' ? true : false", db("a"))).isTrue();
  }

  // --- fail-closed cases ---

  @Test
  void anOrgLessPrincipalHasNoActiveOrgKeyAndFailsClosed() {
    var ctx =
        new EvaluationContext(
            new AuthzPrincipal(USER, null),
            new Action(DATABASE, UPDATE),
            new ResourceRef.Existing(DATABASE, DB),
            new ResourceFacts(ORG, Map.of("name", "x")),
            List.of(),
            List.of());
    assertThat(evaluator.satisfied("resource.org_id == principal.active_org", ctx)).isFalse();
  }

  @Test
  void aToCreateRefHasNoResourceIdAndFailsClosed() {
    var ctx =
        new EvaluationContext(
            new AuthzPrincipal(
                USER, new Membership.UserMembership(new MembershipId(randomUUID()), ORG)),
            new Action(DATABASE, CREATE),
            new ResourceRef.ToCreate(DATABASE, ORG),
            new ResourceFacts(ORG, Map.of()),
            List.of(),
            List.of());
    assertThat(evaluator.satisfied("resource.id == '" + DB + "'", ctx)).isFalse();
  }

  @Test
  void aFactAttributeCannotShadowAServerResolvedKey() {
    // A fact source emitting a reserved key (org_id) must not override the server-resolved value.
    var ctx =
        new EvaluationContext(
            new AuthzPrincipal(
                USER, new Membership.UserMembership(new MembershipId(randomUUID()), ORG)),
            new Action(DATABASE, UPDATE),
            new ResourceRef.Existing(DATABASE, DB),
            new ResourceFacts(ORG, Map.of("org_id", "spoofed", "name", "x")),
            List.of(),
            List.of());
    assertThat(evaluator.satisfied("resource.org_id == '" + ORG.value() + "'", ctx)).isTrue();
    assertThat(evaluator.satisfied("resource.org_id == 'spoofed'", ctx)).isFalse();
  }

  @Test
  void aMissingAttributeFailsClosed() {
    assertThat(evaluator.satisfied("resource.absent == 'x'", db("x"))).isFalse();
  }

  @Test
  void aMalformedConditionFailsClosed() {
    assertThat(evaluator.satisfied("resource.name ==", db("x"))).isFalse();
  }

  @Test
  void aNonBooleanConditionFailsClosed() {
    assertThat(evaluator.satisfied("resource.name", db("x"))).isFalse();
  }

  private static EvaluationContext ownedBy(UserId creator) {
    var member = new Membership.UserMembership(new MembershipId(randomUUID()), ORG);
    return new EvaluationContext(
        new AuthzPrincipal(USER, member),
        new Action(DATABASE, UPDATE),
        new ResourceRef.Existing(DATABASE, DB),
        new ResourceFacts(ORG, Map.of("name", "x", "created_by", creator.value().toString())),
        List.of(),
        List.of());
  }

  private static EvaluationContext db(String databaseName) {
    var member = new Membership.UserMembership(new MembershipId(randomUUID()), ORG);
    return new EvaluationContext(
        new AuthzPrincipal(USER, member),
        new Action(DATABASE, UPDATE),
        new ResourceRef.Existing(DATABASE, DB),
        new ResourceFacts(ORG, Map.of("name", databaseName)),
        List.of(),
        List.of());
  }
}
