package com.beachape.aminam.infra.authz.repositories.json;

import com.beachape.aminam.domain.authz.models.Effect;

enum EffectJson {
  ALLOW,
  DENY;

  Effect toDomain() {
    return switch (this) {
      case ALLOW -> Effect.ALLOW;
      case DENY -> Effect.DENY;
    };
  }

  static EffectJson from(Effect effect) {
    return switch (effect) {
      case ALLOW -> ALLOW;
      case DENY -> DENY;
    };
  }
}
