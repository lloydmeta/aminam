package com.beachape.aminam.app.routes.v1.databases.models;

import com.beachape.aminam.domain.databases.models.Database;
import com.beachape.aminam.domain.databases.models.VisibleDatabase;

public record DatabaseResponse(
    String id, String orgId, String name, String createdBy, boolean editable, String createdAt) {

  public static DatabaseResponse from(VisibleDatabase visible) {
    return of(visible.database(), visible.editable());
  }

  public static DatabaseResponse of(Database database, boolean editable) {
    return new DatabaseResponse(
        database.id().toString(),
        database.orgId().toString(),
        database.name(),
        database.createdBy().value().toString(),
        editable,
        database.createdAt().toString());
  }
}
