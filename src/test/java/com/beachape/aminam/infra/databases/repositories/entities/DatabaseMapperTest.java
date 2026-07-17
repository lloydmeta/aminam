package com.beachape.aminam.infra.databases.repositories.entities;

import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.databases.models.Database;
import com.beachape.aminam.domain.databases.models.DatabaseId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class DatabaseMapperTest {

  private static final DatabaseId ID =
      new DatabaseId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
  private static final OrgId ORG =
      new OrgId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
  private static final UserId CREATOR =
      new UserId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
  private static final Instant T = Instant.parse("2026-06-20T00:00:00Z");

  @Test
  void domainToEntityToDomainRoundTripsAllFields() {
    var database = new Database(ID, ORG, "metrics", CREATOR, T);

    var roundTripped = DatabaseEntity.Mapper.toDomain(DatabaseEntity.Mapper.toEntity(database));

    assertThat(roundTripped).isEqualTo(database);
  }

  @Test
  void applyChangesPreservesCreatedAtAndCreatedBy() {
    var original = new Database(ID, ORG, "original", CREATOR, T);
    var entity = DatabaseEntity.Mapper.toEntity(original);
    // A rename carrying a different creator and timestamp must not move either.
    var renamed =
        new Database(
            original.id(),
            original.orgId(),
            "renamed",
            new UserId(UUID.fromString("44444444-4444-4444-4444-444444444444")),
            Instant.parse("2099-01-01T00:00:00Z"));

    DatabaseEntity.Mapper.applyChanges(entity, renamed);

    assertThat(entity.createdAt).isEqualTo(T);
    assertThat(entity.createdBy).isEqualTo(CREATOR.value());
    assertThat(entity.name).isEqualTo("renamed");
  }
}
