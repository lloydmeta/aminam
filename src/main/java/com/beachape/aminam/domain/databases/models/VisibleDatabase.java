package com.beachape.aminam.domain.databases.models;

/// A database the caller may read, plus whether they may edit it (`database:update`). The
/// editability decision lives in the service, not on the resource.
public record VisibleDatabase(Database database, boolean editable) {}
