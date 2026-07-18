package com.beachape.aminam.domain.authc.models;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class AccessTokenTest {

  @Test
  void toStringDoesNotLeakTheTokenValue() {
    var token = new AccessToken("super-secret-jwt");

    assertThat(token.toString()).doesNotContain("super-secret-jwt");
    assertThat(token.value()).isEqualTo("super-secret-jwt");
  }
}
