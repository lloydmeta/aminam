package com.beachape.aminam.infra.databases.repositories.panache;

import com.beachape.aminam.infra.databases.repositories.entities.DatabaseEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PanacheDatabaseRepository implements PanacheRepositoryBase<DatabaseEntity, UUID> {

  public List<DatabaseEntity> listByOrg(UUID orgId) {
    return list("orgId", Sort.ascending("createdAt").and("id"), orgId);
  }
}
