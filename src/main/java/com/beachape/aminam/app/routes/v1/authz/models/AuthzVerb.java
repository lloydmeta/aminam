package com.beachape.aminam.app.routes.v1.authz.models;

import com.beachape.aminam.domain.authz.models.Verb;

public enum AuthzVerb {
  CREATE,
  READ,
  UPDATE,
  DELETE,
  ATTACH,
  DETACH;

  public Verb toDomain() {
    return switch (this) {
      case CREATE -> Verb.CREATE;
      case READ -> Verb.READ;
      case UPDATE -> Verb.UPDATE;
      case DELETE -> Verb.DELETE;
      case ATTACH -> Verb.ATTACH;
      case DETACH -> Verb.DETACH;
    };
  }
}
