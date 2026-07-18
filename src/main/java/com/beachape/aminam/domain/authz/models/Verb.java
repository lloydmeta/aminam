package com.beachape.aminam.domain.authz.models;

/// CRUD plus grant management. ATTACH/DETACH are valid only on attachment points (membership,
/// database); the policy being attached is gated separately by policy:read.
public enum Verb {
  CREATE,
  READ,
  UPDATE,
  DELETE,
  ATTACH,
  DETACH
}
