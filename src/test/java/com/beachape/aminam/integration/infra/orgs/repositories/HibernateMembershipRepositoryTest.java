package com.beachape.aminam.integration.infra.orgs.repositories;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.beachape.aminam.domain.authc.models.PasswordHash;
import com.beachape.aminam.domain.authc.models.User;
import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.authc.repositories.UserRepository;
import com.beachape.aminam.domain.orgs.models.Membership;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.beachape.aminam.domain.orgs.models.Organisation;
import com.beachape.aminam.domain.orgs.repositories.MembershipRepository;
import com.beachape.aminam.domain.orgs.repositories.MembershipRepository.DuplicateMembershipException;
import com.beachape.aminam.domain.orgs.repositories.OrganisationRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.Test;

@QuarkusTest
final class HibernateMembershipRepositoryTest {

  private static final Instant T = Instant.parse("2026-06-20T00:00:00Z");

  @Inject MembershipRepository memberships;
  @Inject OrganisationRepository organisations;
  @Inject UserRepository users;

  @Test
  void createThenFindReturnsTheMembership() throws Exception {
    var f = fixture();
    var created = memberships.create(membership(f));

    var found = memberships.find(f.principal(), f.org());

    assertThat(found).isEqualTo(created);
  }

  @Test
  void findReturnsNullWhenNotAMember() throws Exception {
    var f = fixture();

    assertThat(memberships.find(f.principal(), f.org())).isNull();
  }

  @Test
  void createWithDuplicatePrincipalAndOrgThrows() throws Exception {
    var f = fixture();
    memberships.create(membership(f));

    assertThatExceptionOfType(DuplicateMembershipException.class)
        .isThrownBy(() -> memberships.create(membership(f)));
  }

  @Test
  void findByIdReturnsTheStoredMembership() throws Exception {
    var f = fixture();
    var created = memberships.create(membership(f));

    assertThat(memberships.findById(created.id())).isEqualTo(created);
  }

  @Test
  void findByIdReturnsNullWhenAbsent() {
    assertThat(memberships.findById(new MembershipId(randomUUID()))).isNull();
  }

  @Test
  void listByOrgReturnsEveryMemberOfTheOrg() throws Exception {
    var f = fixture();
    var first = memberships.create(membership(f));
    var second =
        memberships.create(
            new Membership(new MembershipId(randomUUID()), otherPrincipal(), f.org(), T));

    assertThat(memberships.listByOrg(f.org())).containsExactlyInAnyOrder(first, second);
  }

  @Test
  void listByOrgOrdersByMembershipCreatedAt() throws Exception {
    var f = fixture();
    var later =
        memberships.create(
            new Membership(
                new MembershipId(randomUUID()), otherPrincipal(), f.org(), T.plusSeconds(1)));
    var earlier = memberships.create(membership(f)); // T

    assertThat(memberships.listByOrg(f.org())).containsExactly(earlier, later);
  }

  @Test
  void listByOrgIsEmptyForAnOrgWithNoMembers() throws Exception {
    var f = fixture();

    assertThat(memberships.listByOrg(f.org())).isEmpty();
  }

  @Test
  void deleteRemovesTheMembership() throws Exception {
    var f = fixture();
    var created = memberships.create(membership(f));

    memberships.delete(created);

    assertThat(memberships.find(f.principal(), f.org())).isNull();
    assertThat(memberships.findById(created.id())).isNull();
  }

  private record Fixture(UserId principal, OrgId org) {}

  private Fixture fixture() throws Exception {
    var principal =
        users
            .create(
                new User(
                    new UserId(randomUUID()), "u-" + randomUUID(), new PasswordHash("$2a$10$h"), T))
            .id();
    var org =
        organisations.create(new Organisation(new OrgId(randomUUID()), "acme", principal, T)).id();
    return new Fixture(principal, org);
  }

  private UserId otherPrincipal() throws Exception {
    return users
        .create(
            new User(
                new UserId(randomUUID()), "u-" + randomUUID(), new PasswordHash("$2a$10$h"), T))
        .id();
  }

  private static Membership membership(Fixture f) {
    return new Membership(new MembershipId(randomUUID()), f.principal(), f.org(), T);
  }
}
