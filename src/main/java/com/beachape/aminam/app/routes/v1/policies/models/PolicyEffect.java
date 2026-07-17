package com.beachape.aminam.app.routes.v1.policies.models;

import com.beachape.aminam.domain.authz.models.Effect;

public enum PolicyEffect {
  ALLOW,
  DENY;

  public Effect toDomain() {
    return switch (this) {
      case ALLOW -> Effect.ALLOW;
      case DENY -> Effect.DENY;
    };
  }

  public static PolicyEffect from(Effect effect) {
    return switch (effect) {
      case ALLOW -> ALLOW;
      case DENY -> DENY;
    };
  }
}
