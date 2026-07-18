package com.beachape.aminam.infra.authz.repositories.json;

import com.beachape.aminam.domain.authz.models.Verb;

enum VerbJson {
  CREATE,
  READ,
  UPDATE,
  DELETE,
  ATTACH,
  DETACH;

  Verb toDomain() {
    return switch (this) {
      case CREATE -> Verb.CREATE;
      case READ -> Verb.READ;
      case UPDATE -> Verb.UPDATE;
      case DELETE -> Verb.DELETE;
      case ATTACH -> Verb.ATTACH;
      case DETACH -> Verb.DETACH;
    };
  }

  static VerbJson from(Verb verb) {
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
