package com.beachape.aminam.app.routes.v1.policies.models;

import com.beachape.aminam.domain.authz.models.Verb;

public enum PolicyVerb {
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

  public static PolicyVerb from(Verb verb) {
    return switch (verb) {
      case CREATE -> CREATE;
      case READ -> READ;
      case UPDATE -> UPDATE;
      case DELETE -> DELETE;
      case ATTACH -> ATTACH;
      case DETACH -> DETACH;
    };
  }
}
