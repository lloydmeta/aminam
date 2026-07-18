package com.beachape.aminam.integration.infra.orgs.repositories;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.domain.authc.models.PasswordHash;
import com.beachape.aminam.domain.authc.models.User;
import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.authc.repositories.UserRepository;
import com.beachape.aminam.domain.orgs.models.Membership;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.beachape.aminam.domain.orgs.models.Organisation;
import com.beachape.aminam.domain.orgs.repositories.MembershipRepository;
import com.beachape.aminam.domain.orgs.repositories.OrganisationRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.Test;

@QuarkusTest
final class HibernateOrganisationRepositoryTest {

  private static final Instant T = Instant.parse("2026-06-20T00:00:00Z");

  @Inject OrganisationRepository organisations;
  @Inject MembershipRepository memberships;
  @Inject UserRepository users;

  @Test
  void createPersistsAndFindByIdReturnsTheOrg() throws Exception {
    var creator = newUser();
    var org = newOrg(creator, "acme", T);

    assertThat(organisations.findById(org.id())).isEqualTo(org);
  }

  @Test
  void findByIdReturnsNullWhenAbsent() {
    assertThat(organisations.findById(new OrgId(randomUUID()))).isNull();
  }

  @Test
  void listByMemberOrdersByMembershipDate() throws Exception {
    var user = newUser();
    // Orgs are in the opposite creation order to their join order: ORDER BY o.createdAt would fail.
    var earlierJoined = newOrg(user, "earlier-joined", T.plusSeconds(1));
    var laterJoined = newOrg(user, "later-joined", T);
    newOrg(user, "not-a-member", T); // created but never joined
    seat(user, laterJoined.id(), T.plusSeconds(1));
    seat(user, earlierJoined.id(), T);

    var listed = organisations.listByMember(user);

    assertThat(listed).containsExactly(earlierJoined, laterJoined);
  }

  @Test
  void listByMemberExcludesOtherUsersOrgs() throws Exception {
    var alice = newUser();
    var bob = newUser();
    var alicesOrg = newOrg(alice, "alice-org", T);
    var bobsOrg = newOrg(bob, "bobs-org", T);
    seat(alice, alicesOrg.id(), T);
    seat(bob, bobsOrg.id(), T);

    var listed = organisations.listByMember(alice);

    assertThat(listed).containsExactly(alicesOrg);
    assertThat(listed).doesNotContain(bobsOrg);
  }

  private UserId newUser() throws Exception {
    var user =
        new User(new UserId(randomUUID()), "u-" + randomUUID(), new PasswordHash("$2a$10$h"), T);
    return users.create(user).id();
  }

  private Organisation newOrg(UserId creator, String name, Instant createdAt) {
    return organisations.create(
        new Organisation(new OrgId(randomUUID()), name, creator, createdAt));
  }

  private void seat(UserId principal, OrgId org, Instant membershipCreatedAt) throws Exception {
    memberships.create(
        new Membership(new MembershipId(randomUUID()), principal, org, membershipCreatedAt));
  }
}
