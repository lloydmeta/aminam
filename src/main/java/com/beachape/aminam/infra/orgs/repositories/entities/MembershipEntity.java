package com.beachape.aminam.infra.orgs.repositories.entities;

import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.orgs.models.Membership;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "memberships")
public class MembershipEntity {

  @Id
  @Column(name = "id", nullable = false)
  UUID id;

  @Column(name = "principal_id", nullable = false)
  UUID principalId;

  @Column(name = "org_id", nullable = false)
  UUID orgId;

  @Column(name = "created_at", nullable = false)
  Instant createdAt;

  public static final class Mapper {

    private Mapper() {}

    public static Membership toDomain(MembershipEntity entity) {
      return new Membership(
          new MembershipId(entity.id),
          new UserId(entity.principalId),
          new OrgId(entity.orgId),
          entity.createdAt);
    }

    public static MembershipEntity toEntity(Membership membership) {
      var entity = new MembershipEntity();
      entity.id = membership.id().value();
      entity.principalId = membership.userId().value();
      entity.orgId = membership.orgId().value();
      entity.createdAt = membership.createdAt();
      return entity;
    }
  }
}
