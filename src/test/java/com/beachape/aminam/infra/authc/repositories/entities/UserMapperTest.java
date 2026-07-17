package com.beachape.aminam.infra.authc.repositories.entities;

import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.domain.authc.models.PasswordHash;
import com.beachape.aminam.domain.authc.models.User;
import com.beachape.aminam.domain.authc.models.UserId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class UserMapperTest {

  @Test
  void domainToEntityToDomainRoundTripsAllFields() {
    var user =
        new User(
            new UserId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
            "lloyd",
            new PasswordHash("$2a$10$hash"),
            Instant.parse("2026-06-18T00:00:00Z"));

    var roundTripped = UserEntity.Mapper.toDomain(UserEntity.Mapper.toEntity(user));

    assertThat(roundTripped).isEqualTo(user);
  }
}
