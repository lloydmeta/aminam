package com.beachape.aminam.integration.infra.databases.repositories;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.beachape.aminam.domain.authc.models.PasswordHash;
import com.beachape.aminam.domain.authc.models.User;
import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.authc.repositories.UserRepository;
import com.beachape.aminam.domain.databases.models.Database;
import com.beachape.aminam.domain.databases.models.DatabaseId;
import com.beachape.aminam.domain.databases.repositories.DatabaseRepository;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.beachape.aminam.domain.orgs.models.Organisation;
import com.beachape.aminam.domain.orgs.repositories.OrganisationRepository;
import com.beachape.aminam.domain.repositories.errors.EntityNotFoundException;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.Test;

@QuarkusTest
final class HibernateDatabaseRepositoryTest {

  private static final Instant T = Instant.parse("2026-06-20T00:00:00Z");

  @Inject DatabaseRepository databases;
  @Inject OrganisationRepository organisations;
  @Inject UserRepository users;

  @Test
  void createPersistsAndFindByIdReturnsIt() throws Exception {
    var f = fixture();
    var database =
        databases.create(
            new Database(new DatabaseId(randomUUID()), f.org(), "metrics", f.principal(), T));

    assertThat(databases.findById(database.id())).isEqualTo(database);
  }

  @Test
  void findByIdReturnsNullWhenAbsent() {
    assertThat(databases.findById(new DatabaseId(randomUUID()))).isNull();
  }

  @Test
  void listByOrgReturnsOnlyThatOrgsDatabases() throws Exception {
    var f = fixture();
    var other = fixture();
    var a =
        databases.create(
            new Database(new DatabaseId(randomUUID()), f.org(), "a", f.principal(), T));
    var b =
        databases.create(
            new Database(new DatabaseId(randomUUID()), f.org(), "b", f.principal(), T));
    databases.create(
        new Database(new DatabaseId(randomUUID()), other.org(), "c", other.principal(), T));

    assertThat(databases.listByOrg(f.org())).containsExactlyInAnyOrder(a, b);
  }

  @Test
  void listByOrgOrdersByCreatedAt() throws Exception {
    var f = fixture();
    var later =
        databases.create(
            new Database(
                new DatabaseId(randomUUID()), f.org(), "later", f.principal(), T.plusSeconds(1)));
    var earlier =
        databases.create(
            new Database(new DatabaseId(randomUUID()), f.org(), "earlier", f.principal(), T));

    assertThat(databases.listByOrg(f.org())).containsExactly(earlier, later);
  }

  @Test
  void updatePersistsTheNewName() throws Exception {
    var f = fixture();
    var database =
        databases.create(
            new Database(new DatabaseId(randomUUID()), f.org(), "metrics", f.principal(), T));

    databases.update(new Database(database.id(), f.org(), "renamed", f.principal(), T));

    assertThat(databases.findById(database.id()))
        .isEqualTo(new Database(database.id(), f.org(), "renamed", f.principal(), T));
  }

  @Test
  void updateDoesNotMoveCreatedBy() throws Exception {
    var f = fixture();
    var other = fixture();
    var database =
        databases.create(
            new Database(new DatabaseId(randomUUID()), f.org(), "metrics", f.principal(), T));

    // The column is immutable, so a renaming write naming a different creator must not move it.
    databases.update(new Database(database.id(), f.org(), "renamed", other.principal(), T));

    assertThat(databases.findById(database.id()))
        .isEqualTo(new Database(database.id(), f.org(), "renamed", f.principal(), T));
  }

  @Test
  void deleteRemovesTheRow() throws Exception {
    var f = fixture();
    var database =
        databases.create(
            new Database(new DatabaseId(randomUUID()), f.org(), "metrics", f.principal(), T));

    databases.delete(database);

    assertThat(databases.findById(database.id())).isNull();
  }

  @Test
  void updateOfAnAbsentIdThrowsRatherThanInserting() {
    // Never persisted, so the org and creator foreign keys never fire.
    var ghost =
        new Database(
            new DatabaseId(randomUUID()),
            new OrgId(randomUUID()),
            "x",
            new UserId(randomUUID()),
            T);

    assertThatExceptionOfType(EntityNotFoundException.class)
        .isThrownBy(() -> databases.update(ghost));
    assertThat(databases.findById(ghost.id())).isNull();
  }

  @Test
  void namesAreNotUniqueWithinAnOrg() throws Exception {
    var f = fixture();
    var first =
        databases.create(
            new Database(new DatabaseId(randomUUID()), f.org(), "dup", f.principal(), T));
    var second =
        databases.create(
            new Database(new DatabaseId(randomUUID()), f.org(), "dup", f.principal(), T));

    assertThat(databases.listByOrg(f.org())).containsExactlyInAnyOrder(first, second);
  }

  private record Fixture(UserId principal, OrgId org) {}

  private Fixture fixture() throws Exception {
    var principal =
        users
            .create(
                new User(
                    new UserId(randomUUID()), "u-" + randomUUID(), new PasswordHash("$2a$10$h"), T))
            .id();
    var org =
        organisations.create(new Organisation(new OrgId(randomUUID()), "acme", principal, T)).id();
    return new Fixture(principal, org);
  }
}
