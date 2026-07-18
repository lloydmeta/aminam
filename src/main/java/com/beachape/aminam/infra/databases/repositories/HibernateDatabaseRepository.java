package com.beachape.aminam.infra.databases.repositories;

import com.beachape.aminam.domain.databases.models.Database;
import com.beachape.aminam.domain.databases.models.DatabaseId;
import com.beachape.aminam.domain.databases.repositories.DatabaseRepository;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.beachape.aminam.domain.repositories.errors.EntityNotFoundException;
import com.beachape.aminam.infra.databases.repositories.entities.DatabaseEntity;
import com.beachape.aminam.infra.databases.repositories.panache.PanacheDatabaseRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import org.jspecify.annotations.Nullable;

@ApplicationScoped
class HibernateDatabaseRepository implements DatabaseRepository {

  @Inject PanacheDatabaseRepository panache;

  @Override
  @Transactional
  public Database create(Database database) {
    var entity = DatabaseEntity.Mapper.toEntity(database);
    panache.persist(entity);
    return DatabaseEntity.Mapper.toDomain(entity);
  }

  @Override
  @Transactional
  public @Nullable Database findById(DatabaseId id) {
    var entity = panache.findById(id.value());
    return entity == null ? null : DatabaseEntity.Mapper.toDomain(entity);
  }

  @Override
  @Transactional
  public List<Database> listByOrg(OrgId orgId) {
    return panache.listByOrg(orgId.value()).stream().map(DatabaseEntity.Mapper::toDomain).toList();
  }

  @Override
  @Transactional
  public Database update(Database database) throws EntityNotFoundException {
    var entity = panache.findById(database.id().value());
    if (entity == null) {
      throw new EntityNotFoundException();
    }
    // Mutate the managed entity; dirty checking flushes the UPDATE.
    DatabaseEntity.Mapper.applyChanges(entity, database);
    return DatabaseEntity.Mapper.toDomain(entity);
  }

  @Override
  @Transactional
  public void delete(Database database) {
    panache.deleteById(database.id().value());
  }
}
