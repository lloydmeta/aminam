package com.beachape.aminam.infra.orgs.repositories.entities;

import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.orgs.models.Membership;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class MembershipMapperTest {

  @Test
  void domainToEntityToDomainRoundTripsAllFields() {
    var membership =
        new Membership(
            new MembershipId(UUID.fromString("33333333-3333-3333-3333-333333333333")),
            new UserId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
            new OrgId(UUID.fromString("22222222-2222-2222-2222-222222222222")),
            Instant.parse("2026-06-20T00:00:00Z"));

    var roundTripped =
        MembershipEntity.Mapper.toDomain(MembershipEntity.Mapper.toEntity(membership));

    assertThat(roundTripped).isEqualTo(membership);
  }
}
