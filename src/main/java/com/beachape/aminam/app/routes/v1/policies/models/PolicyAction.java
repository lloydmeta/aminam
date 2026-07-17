package com.beachape.aminam.app.routes.v1.policies.models;

import com.beachape.aminam.domain.authz.models.Action;
import jakarta.validation.constraints.NotNull;

public record PolicyAction(@NotNull PolicyResourceType type, @NotNull PolicyVerb verb) {

  Action toDomain() {
    return new Action(type.toDomain(), verb.toDomain());
  }

  static PolicyAction from(Action action) {
    return new PolicyAction(PolicyResourceType.from(action.type()), PolicyVerb.from(action.verb()));
  }
}
