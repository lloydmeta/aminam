package com.beachape.aminam.domain.databases.services;

import com.beachape.aminam.domain.authz.models.ConditionAttributes;
import com.beachape.aminam.domain.authz.models.ResourceFacts;
import com.beachape.aminam.domain.authz.models.ResourceType;
import com.beachape.aminam.domain.authz.repositories.ResourceFactSource;
import com.beachape.aminam.domain.databases.models.DatabaseId;
import com.beachape.aminam.domain.databases.repositories.DatabaseRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Resolves a database resource to its facts. A database is owned by its org.
@ApplicationScoped
public class DatabaseFactSource implements ResourceFactSource {

  private final DatabaseRepository databases;

  @Inject
  DatabaseFactSource(DatabaseRepository databases) {
    this.databases = databases;
  }

  @Override
  public Set<ResourceType> types() {
    return Set.of(ResourceType.DATABASE);
  }

  @Override
  public @Nullable ResourceFacts resolve(UUID id) {
    var database = databases.findById(new DatabaseId(id));
    return database == null
        ? null
        : new ResourceFacts(
            database.orgId(),
            Map.of(
                ConditionAttributes.NAME,
                database.name(),
                ConditionAttributes.CREATED_BY,
                database.createdBy().value().toString()));
  }
}
