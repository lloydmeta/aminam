package com.beachape.aminam.infra.authz.repositories.json;

import static com.beachape.aminam.domain.authz.models.ResourcePattern.wildcard;
import static com.beachape.aminam.domain.authz.models.ResourceType.DATABASE;
import static com.beachape.aminam.domain.authz.models.Verb.UPDATE;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.domain.authz.models.Action;
import com.beachape.aminam.domain.authz.models.Effect;
import com.beachape.aminam.domain.authz.models.PolicyDocument;
import com.beachape.aminam.domain.authz.models.ResourcePattern;
import com.beachape.aminam.domain.authz.models.Statement;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class PolicyDocumentJsonMapperTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Test
  void roundTripsADocumentThroughTheModelAndBackToDomain() {
    var document = document();

    var restored =
        PolicyDocumentJson.Mapper.toDomain(PolicyDocumentJson.Mapper.fromDomain(document));

    assertThat(restored).isEqualTo(document);
  }

  @Test
  void serialisesBareTypesAndRoundTripsThroughJackson() throws Exception {
    var membership = new MembershipId(randomUUID());
    var dbId = randomUUID();
    var document =
        new PolicyDocument(
            List.of(
                new Statement(
                    Effect.ALLOW,
                    Set.of(membership),
                    Set.of(new Action(DATABASE, UPDATE)),
                    Set.of(wildcard(DATABASE), new ResourcePattern(DATABASE, dbId)),
                    "resource.name.startsWith('report-')")));
    var model = PolicyDocumentJson.Mapper.fromDomain(document);

    var serialised = JSON.writeValueAsString(model);

    assertThat(serialised)
        .contains("\"effect\":\"ALLOW\"")
        .contains("\"type\":\"DATABASE\"")
        .contains("\"verb\":\"UPDATE\"")
        .contains("\"id\":\"" + dbId + "\"")
        .contains(membership.value().toString())
        .contains("resource.name.startsWith('report-')");
    assertThat(JSON.readValue(serialised, PolicyDocumentJson.class)).isEqualTo(model);
  }

  @Test
  void identityStatementHasNoMemberships() {
    var document =
        new PolicyDocument(
            List.of(
                new Statement(
                    Effect.ALLOW,
                    Set.of(),
                    Set.of(new Action(DATABASE, UPDATE)),
                    Set.of(wildcard(DATABASE)),
                    null)));

    var model = PolicyDocumentJson.Mapper.fromDomain(document);

    assertThat(model.statements().getFirst().memberships()).isEmpty();
    assertThat(model.statements().getFirst().condition()).isNull();
    assertThat(PolicyDocumentJson.Mapper.toDomain(model)).isEqualTo(document);
  }

  private static PolicyDocument document() {
    var first = new MembershipId(randomUUID());
    var second = new MembershipId(randomUUID());
    return new PolicyDocument(
        List.of(
            new Statement(
                Effect.ALLOW,
                Set.of(first, second),
                Set.of(new Action(DATABASE, UPDATE)),
                Set.of(wildcard(DATABASE), new ResourcePattern(DATABASE, randomUUID())),
                "resource.name.startsWith('report-')"),
            new Statement(
                Effect.DENY,
                Set.of(),
                Set.of(new Action(DATABASE, UPDATE)),
                Set.of(wildcard(DATABASE)),
                null)));
  }
}
