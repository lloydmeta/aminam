package com.beachape.aminam.infra.authc.repositories;

import com.beachape.aminam.domain.authc.models.User;
import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.authc.repositories.UserRepository;
import com.beachape.aminam.domain.errors.DomainException;
import com.beachape.aminam.infra.authc.repositories.entities.UserEntity;
import com.beachape.aminam.infra.authc.repositories.panache.PanacheUserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@ApplicationScoped
class HibernateUserRepository implements UserRepository {

  private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

  @Inject PanacheUserRepository panache;

  @Override
  // JTA does not roll back on checked exceptions by default ...
  @Transactional(rollbackOn = DomainException.class)
  public User create(User user) throws DuplicateUsernameException {
    var entity = UserEntity.Mapper.toEntity(user);
    try {
      panache.persist(entity);
      panache.flush(); // surface a duplicate-username violation here, not at commit
      return UserEntity.Mapper.toDomain(entity);
    } catch (PersistenceException e) {
      if (isUniqueViolation(e)) {
        throw new DuplicateUsernameException(user.username(), e);
      }
      throw e;
    }
  }

  @Override
  @Transactional
  public @Nullable User findByUsername(String username) {
    return panache.findByUsername(username).map(UserEntity.Mapper::toDomain).orElse(null);
  }

  @Override
  @Transactional
  public List<User> findByIds(Collection<UserId> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    var rawIds = ids.stream().map(UserId::value).toList();
    return panache.listByIds(rawIds).stream().map(UserEntity.Mapper::toDomain).toList();
  }

  private static boolean isUniqueViolation(Throwable t) {
    for (Throwable cause = t; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql
          && Objects.equals(sql.getSQLState(), UNIQUE_VIOLATION_SQL_STATE)) {
        return true;
      }
    }
    return false;
  }
}
