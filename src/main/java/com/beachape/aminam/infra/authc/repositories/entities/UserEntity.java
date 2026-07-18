package com.beachape.aminam.infra.authc.repositories.entities;

import com.beachape.aminam.domain.authc.models.PasswordHash;
import com.beachape.aminam.domain.authc.models.User;
import com.beachape.aminam.domain.authc.models.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "principals")
public class UserEntity {

  @Id
  @Column(name = "id", nullable = false)
  UUID id;

  @Column(name = "username", nullable = false, unique = true)
  String username;

  @Column(name = "password_hash", nullable = false)
  String passwordHash;

  @Column(name = "created_at", nullable = false)
  Instant createdAt;

  public static final class Mapper {

    private Mapper() {}

    public static User toDomain(UserEntity entity) {
      return new User(
          new UserId(entity.id),
          entity.username,
          new PasswordHash(entity.passwordHash),
          entity.createdAt);
    }

    public static UserEntity toEntity(User user) {
      var entity = new UserEntity();
      entity.id = user.id().value();
      entity.username = user.username();
      entity.passwordHash = user.passwordHash().value();
      entity.createdAt = user.createdAt();
      return entity;
    }
  }
}
