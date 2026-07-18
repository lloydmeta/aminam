package com.beachape.aminam.domain.databases.repositories;

import com.beachape.aminam.domain.databases.models.Database;
import com.beachape.aminam.domain.databases.models.DatabaseId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.beachape.aminam.domain.repositories.errors.EntityNotFoundException;
import java.util.List;
import org.jspecify.annotations.Nullable;

public interface DatabaseRepository {

  Database create(Database database);

  @Nullable Database findById(DatabaseId id);

  List<Database> listByOrg(OrgId orgId);

  Database update(Database database) throws EntityNotFoundException;

  void delete(Database database);
}
