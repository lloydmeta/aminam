package com.beachape.aminam.domain.orgs.services;

import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.authz.models.ResourceType;
import com.beachape.aminam.domain.orgs.models.Membership;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.beachape.aminam.domain.orgs.repositories.MembershipRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;

final class MembershipFactSourceTest {

  private final MembershipRepository memberships = mock();
  private final MembershipFactSource source = new MembershipFactSource(memberships);

  @Test
  void resolvesOwningOrgFromTheMembership() {
    var id = randomUUID();
    var org = new OrgId(randomUUID());
    when(memberships.findById(new MembershipId(id)))
        .thenReturn(
            new Membership(
                new MembershipId(id),
                new UserId(randomUUID()),
                org,
                Instant.parse("2026-06-20T00:00:00Z")));

    assertThat(requireNonNull(source.resolve(id)).owningOrg()).isEqualTo(org);
  }

  @Test
  void returnsNullWhenAbsent() {
    var id = randomUUID();
    when(memberships.findById(new MembershipId(id))).thenReturn(null);

    assertThat(source.resolve(id)).isNull();
  }

  @Test
  void declaresMembershipAndSelfMembershipTypes() {
    assertThat(source.types())
        .containsExactlyInAnyOrder(ResourceType.MEMBERSHIP, ResourceType.SELF_MEMBERSHIP);
  }
}
