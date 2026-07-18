package com.beachape.aminam.infra.orgs.repositories.entities;

import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.beachape.aminam.domain.orgs.models.Organisation;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class OrgMapperTest {

  @Test
  void domainToEntityToDomainRoundTripsAllFields() {
    var org =
        new Organisation(
            new OrgId(UUID.fromString("22222222-2222-2222-2222-222222222222")),
            "acme",
            new UserId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
            Instant.parse("2026-06-20T00:00:00Z"));

    var roundTripped = OrgEntity.Mapper.toDomain(OrgEntity.Mapper.toEntity(org));

    assertThat(roundTripped).isEqualTo(org);
  }
}
