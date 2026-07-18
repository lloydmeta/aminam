package com.beachape.aminam.infra.authc.services;

import com.beachape.aminam.domain.authc.models.TokenId;
import com.beachape.aminam.domain.authc.services.TokenRevocationService;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;

@ApplicationScoped
class RedisRevocationService implements TokenRevocationService {

  private static final String KEY_PREFIX = "revoked:jti:";

  private final ValueCommands<String, String> values;

  @Inject
  RedisRevocationService(RedisDataSource redis) {
    this.values = redis.value(String.class);
  }

  @Override
  public void revoke(TokenId jti, Duration ttl) {
    if (ttl.isZero() || ttl.isNegative()) {
      return;
    }
    values.setex(KEY_PREFIX + jti.value(), ttl.toSeconds(), "revoked");
  }

  @Override
  public boolean isRevoked(TokenId jti) {
    return values.get(KEY_PREFIX + jti.value()) != null;
  }
}
