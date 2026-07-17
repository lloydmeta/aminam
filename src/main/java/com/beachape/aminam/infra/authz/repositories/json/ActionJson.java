package com.beachape.aminam.infra.authz.repositories.json;

import com.beachape.aminam.domain.authz.models.Action;

record ActionJson(ResourceTypeJson type, VerbJson verb) {

  Action toDomain() {
    return new Action(type.toDomain(), verb.toDomain());
  }

  static ActionJson from(Action action) {
    return new ActionJson(ResourceTypeJson.from(action.type()), VerbJson.from(action.verb()));
  }
}
