package com.beachape.aminam.infra.authc.repositories.panache;

import com.beachape.aminam.infra.authc.repositories.entities.UserEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheUserRepository implements PanacheRepositoryBase<UserEntity, UUID> {

  public Optional<UserEntity> findByUsername(String username) {
    return find("username", username).firstResultOptional();
  }

  public List<UserEntity> listByIds(Collection<UUID> ids) {
    return list("id in ?1", ids);
  }
}
