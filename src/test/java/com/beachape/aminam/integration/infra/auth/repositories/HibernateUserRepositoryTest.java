package com.beachape.aminam.integration.infra.auth.repositories;

import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.beachape.aminam.domain.authc.models.PasswordHash;
import com.beachape.aminam.domain.authc.models.User;
import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.authc.repositories.UserRepository;
import com.beachape.aminam.domain.authc.repositories.UserRepository.DuplicateUsernameException;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
final class HibernateUserRepositoryTest {

  private static final Instant CREATED_AT = Instant.parse("2026-06-18T00:00:00Z");

  @Inject UserRepository repository;

  @Test
  void createPersistsAndReturnsTheUser() throws Exception {
    var user = newUser("lloyd-" + randomUUID());

    var created = repository.create(user);

    assertThat(created).returns(user.username(), User::username).returns(user.id(), User::id);
  }

  @Test
  void createWithDuplicateUsernameThrows() throws Exception {
    var username = "meta-" + randomUUID();
    repository.create(newUser(username));

    assertThatExceptionOfType(DuplicateUsernameException.class)
        .isThrownBy(() -> repository.create(newUser(username)));
  }

  @Test
  void findByUsernameReturnsTheStoredUser() throws Exception {
    var username = "beachape-" + randomUUID();
    var created = repository.create(newUser(username));

    var found = requireNonNull(repository.findByUsername(username));

    assertThat(found.id()).isEqualTo(created.id());
    assertThat(found.username()).isEqualTo(username);
  }

  @Test
  void findByUsernameReturnsNullWhenAbsent() {
    assertThat(repository.findByUsername("missing-" + randomUUID())).isNull();
  }

  @Test
  void findByIdsReturnsTheMatchingUsersAndIgnoresAbsentIds() throws Exception {
    var alice = repository.create(newUser("alice-" + randomUUID()));
    var lloyd = repository.create(newUser("lloyd-" + randomUUID()));

    var found = repository.findByIds(List.of(alice.id(), lloyd.id(), new UserId(randomUUID())));

    assertThat(found).extracting(User::id).containsExactlyInAnyOrder(alice.id(), lloyd.id());
  }

  @Test
  void findByIdsWithNoIdsReturnsEmpty() {
    assertThat(repository.findByIds(List.of())).isEmpty();
  }

  private static User newUser(String username) {
    return new User(
        new UserId(randomUUID()), username, new PasswordHash("$2a$10$hash"), CREATED_AT);
  }
}
