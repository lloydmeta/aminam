package com.beachape.aminam.infra.authz.repositories.panache;

import com.beachape.aminam.infra.authz.repositories.entities.PolicyEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PanachePolicyRepository implements PanacheRepositoryBase<PolicyEntity, UUID> {

  public List<PolicyEntity> listByOrg(UUID orgId) {
    return list("orgId", Sort.ascending("createdAt").and("id"), orgId);
  }
}
