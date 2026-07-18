package com.beachape.aminam.integration.infra.authz.repositories;

import static com.beachape.aminam.domain.authz.models.ResourcePattern.wildcard;
import static com.beachape.aminam.domain.authz.models.ResourceType.DATABASE;
import static com.beachape.aminam.domain.authz.models.Verb.UPDATE;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.beachape.aminam.domain.authc.models.PasswordHash;
import com.beachape.aminam.domain.authc.models.User;
import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.authc.repositories.UserRepository;
import com.beachape.aminam.domain.authz.models.Action;
import com.beachape.aminam.domain.authz.models.Effect;
import com.beachape.aminam.domain.authz.models.Policy;
import com.beachape.aminam.domain.authz.models.PolicyDocument;
import com.beachape.aminam.domain.authz.models.PolicyId.CustomPolicyId;
import com.beachape.aminam.domain.authz.models.ResourcePattern;
import com.beachape.aminam.domain.authz.models.Statement;
import com.beachape.aminam.domain.authz.repositories.PolicyRepository;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.beachape.aminam.domain.orgs.models.Organisation;
import com.beachape.aminam.domain.orgs.repositories.OrganisationRepository;
import com.beachape.aminam.domain.repositories.errors.EntityNotFoundException;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

@QuarkusTest
final class HibernatePolicyRepositoryTest {

  private static final Instant T = Instant.parse("2026-06-23T00:00:00Z");

  @Inject PolicyRepository policies;
  @Inject OrganisationRepository organisations;
  @Inject UserRepository users;

  @Test
  void createPersistsAndFindByIdRoundTripsTheWholeDocument() throws Exception {
    var org = newOrg();
    var policy = policy(org, "reports", documentWithMembershipAndCondition());

    var created = policies.create(policy);

    assertThat(policies.findById(created.id())).isEqualTo(policy);
  }

  @Test
  void findByIdReturnsNullForAnUnknownId() {
    assertThat(policies.findById(new CustomPolicyId(randomUUID()))).isNull();
  }

  @Test
  void listByOrgReturnsOnlyThatOrgsPolicies() throws Exception {
    var org = newOrg();
    var other = newOrg();
    var a = policies.create(policy(org, "a", identityDocument()));
    var b = policies.create(policy(org, "b", identityDocument()));
    policies.create(policy(other, "c", identityDocument()));

    assertThat(policies.listByOrg(org)).containsExactlyInAnyOrder(a, b);
  }

  @Test
  void listByOrgOrdersByCreatedAt() throws Exception {
    var org = newOrg();
    var later =
        policies.create(
            new Policy(
                new CustomPolicyId(randomUUID()),
                org,
                "later",
                identityDocument(),
                T.plusSeconds(1)));
    var earlier = policies.create(policy(org, "earlier", identityDocument())); // T

    assertThat(policies.listByOrg(org)).containsExactly(earlier, later);
  }

  @Test
  void updatePersistsTheNewNameAndDocument() throws Exception {
    var org = newOrg();
    var policy = policies.create(policy(org, "before", identityDocument()));
    var edited =
        new Policy(
            policy.id(), org, "after", documentWithMembershipAndCondition(), policy.createdAt());

    policies.update(edited);

    assertThat(policies.findById(policy.id())).isEqualTo(edited);
  }

  @Test
  void updateOfAnAbsentIdThrowsRatherThanInserting() {
    var ghost = policy(new OrgId(randomUUID()), "ghost", identityDocument());

    assertThatExceptionOfType(EntityNotFoundException.class)
        .isThrownBy(() -> policies.update(ghost));
    assertThat(policies.findById(ghost.id())).isNull();
  }

  @Test
  void deleteRemovesTheRow() throws Exception {
    var org = newOrg();
    var policy = policies.create(policy(org, "doomed", identityDocument()));

    policies.delete(policy);

    assertThat(policies.findById(policy.id())).isNull();
  }

  private OrgId newOrg() throws Exception {
    var user =
        users
            .create(
                new User(
                    new UserId(randomUUID()), "u-" + randomUUID(), new PasswordHash("$2a$10$h"), T))
            .id();
    return organisations.create(new Organisation(new OrgId(randomUUID()), "acme", user, T)).id();
  }

  private static Policy policy(OrgId org, String name, PolicyDocument document) {
    return new Policy(new CustomPolicyId(randomUUID()), org, name, document, T);
  }

  private static PolicyDocument identityDocument() {
    return new PolicyDocument(
        List.of(
            new Statement(
                Effect.ALLOW,
                Set.of(),
                Set.of(new Action(DATABASE, UPDATE)),
                Set.of(wildcard(DATABASE)),
                null)));
  }

  private static PolicyDocument documentWithMembershipAndCondition() {
    return new PolicyDocument(
        List.of(
            new Statement(
                Effect.ALLOW,
                Set.of(new MembershipId(randomUUID())),
                Set.of(new Action(DATABASE, UPDATE)),
                Set.of(wildcard(DATABASE), new ResourcePattern(DATABASE, randomUUID())),
                "resource.name.startsWith('report-')")));
  }
}
