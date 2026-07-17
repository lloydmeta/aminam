package com.beachape.aminam.domain.databases.services;

import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.authz.models.ConditionAttributes;
import com.beachape.aminam.domain.authz.models.ResourceType;
import com.beachape.aminam.domain.databases.models.Database;
import com.beachape.aminam.domain.databases.models.DatabaseId;
import com.beachape.aminam.domain.databases.repositories.DatabaseRepository;
import com.beachape.aminam.domain.orgs.models.OrgId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

final class DatabaseFactSourceTest {

  private static final Instant T = Instant.parse("2026-06-20T00:00:00Z");

  private final DatabaseRepository databases = mock();
  private final DatabaseFactSource source = new DatabaseFactSource(databases);

  @Test
  void resolvesOwningOrgFromTheDatabase() {
    var id = randomUUID();
    var org = new OrgId(randomUUID());
    when(databases.findById(new DatabaseId(id)))
        .thenReturn(new Database(new DatabaseId(id), org, "metrics", new UserId(randomUUID()), T));

    assertThat(requireNonNull(source.resolve(id)).owningOrg()).isEqualTo(org);
  }

  @Test
  void contributesNameAndCreatedByAsAttributes() {
    var id = randomUUID();
    var creator = new UserId(randomUUID());
    when(databases.findById(new DatabaseId(id)))
        .thenReturn(
            new Database(new DatabaseId(id), new OrgId(randomUUID()), "metrics", creator, T));

    assertThat(requireNonNull(source.resolve(id)).attributes())
        .containsEntry(ConditionAttributes.NAME, "metrics")
        .containsEntry(ConditionAttributes.CREATED_BY, creator.value().toString());
  }

  @Test
  void returnsNullWhenAbsent() {
    var id = randomUUID();
    when(databases.findById(new DatabaseId(id))).thenReturn(null);

    assertThat(source.resolve(id)).isNull();
  }

  @Test
  void declaresDatabaseType() {
    assertThat(source.types()).containsExactly(ResourceType.DATABASE);
  }
}
