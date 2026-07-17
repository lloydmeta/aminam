package com.beachape.aminam.infra.orgs.repositories.panache;

import com.beachape.aminam.infra.orgs.repositories.entities.OrgEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PanacheOrgRepository implements PanacheRepositoryBase<OrgEntity, UUID> {

  public List<OrgEntity> listByMember(UUID principalId) {
    return list(
        "SELECT o FROM OrgEntity o, MembershipEntity m "
            + "WHERE m.orgId = o.id AND m.principalId = ?1 "
            + "ORDER BY m.createdAt, m.id",
        principalId);
  }
}
