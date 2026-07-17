package com.beachape.aminam.infra.orgs.repositories.panache;

import com.beachape.aminam.infra.orgs.repositories.entities.MembershipEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheMembershipRepository implements PanacheRepositoryBase<MembershipEntity, UUID> {

  public Optional<MembershipEntity> find(UUID principalId, UUID orgId) {
    return find("principalId = ?1 and orgId = ?2", principalId, orgId).firstResultOptional();
  }

  public List<MembershipEntity> listByOrg(UUID orgId) {
    return list("orgId", Sort.ascending("createdAt").and("id"), orgId);
  }
}
