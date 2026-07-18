package com.beachape.aminam.infra.orgs.repositories.entities;

import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.beachape.aminam.domain.orgs.models.Organisation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orgs")
public class OrgEntity {

  @Id
  @Column(name = "id", nullable = false)
  UUID id;

  @Column(name = "name", nullable = false)
  String name;

  @Column(name = "created_by", nullable = false)
  UUID createdBy;

  @Column(name = "created_at", nullable = false)
  Instant createdAt;

  public static final class Mapper {

    private Mapper() {}

    public static Organisation toDomain(OrgEntity entity) {
      return new Organisation(
          new OrgId(entity.id), entity.name, new UserId(entity.createdBy), entity.createdAt);
    }

    public static OrgEntity toEntity(Organisation org) {
      var entity = new OrgEntity();
      entity.id = org.id().value();
      entity.name = org.name();
      entity.createdBy = org.createdBy().value();
      entity.createdAt = org.createdAt();
      return entity;
    }
  }
}
