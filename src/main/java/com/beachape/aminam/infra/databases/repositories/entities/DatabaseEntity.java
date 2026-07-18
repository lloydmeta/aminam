package com.beachape.aminam.infra.databases.repositories.entities;

import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.databases.models.Database;
import com.beachape.aminam.domain.databases.models.DatabaseId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "managed_databases")
public class DatabaseEntity {

  @Id
  @Column(name = "id", nullable = false)
  UUID id;

  @Column(name = "org_id", nullable = false)
  UUID orgId;

  @Column(name = "name", nullable = false)
  String name;

  @Column(name = "created_by", nullable = false)
  UUID createdBy;

  @Column(name = "created_at", nullable = false)
  Instant createdAt;

  public static final class Mapper {

    private Mapper() {}

    public static Database toDomain(DatabaseEntity entity) {
      return new Database(
          new DatabaseId(entity.id),
          new OrgId(entity.orgId),
          entity.name,
          new UserId(entity.createdBy),
          entity.createdAt);
    }

    public static DatabaseEntity toEntity(Database database) {
      var entity = new DatabaseEntity();
      entity.id = database.id().value();
      entity.orgId = database.orgId().value();
      entity.name = database.name();
      entity.createdBy = database.createdBy().value();
      entity.createdAt = database.createdAt();
      return entity;
    }

    public static void applyChanges(DatabaseEntity entity, Database database) {
      entity.name = database.name(); // id, orgId, createdBy and createdAt are immutable
    }
  }
}
