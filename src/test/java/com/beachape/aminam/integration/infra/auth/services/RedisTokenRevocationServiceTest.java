package com.beachape.aminam.integration.infra.auth.services;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.domain.authc.models.TokenId;
import com.beachape.aminam.domain.authc.services.TokenRevocationService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import org.junit.jupiter.api.Test;

@QuarkusTest
final class RedisTokenRevocationServiceTest {

  @Inject TokenRevocationService revokeService;

  @Test
  void revokedJtiIsReportedAsRevoked() {
    var jti = randomJti();

    revokeService.revoke(jti, Duration.ofMinutes(5));

    assertThat(revokeService.isRevoked(jti)).isTrue();
  }

  @Test
  void unknownJtiIsNotRevoked() {
    assertThat(revokeService.isRevoked(randomJti())).isFalse();
  }

  @Test
  void revokeWithNonPositiveTtlIsANoOp() {
    var jti = randomJti();

    revokeService.revoke(jti, Duration.ZERO);

    assertThat(revokeService.isRevoked(jti)).isFalse();
  }

  private static TokenId randomJti() {
    return new TokenId(randomUUID().toString());
  }
}
