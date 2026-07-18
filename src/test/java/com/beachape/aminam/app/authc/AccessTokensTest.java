package com.beachape.aminam.app.authc;

import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.domain.authc.models.AccessToken;
import org.junit.jupiter.api.Test;

final class AccessTokensTest {

  @Test
  void prefersTheBearerHeader() {
    assertThat(AccessTokens.resolve("Bearer header-token", "cookie-token"))
        .isEqualTo(new AccessToken("header-token"));
  }

  @Test
  void fallsBackToTheCookieWhenNoBearerHeader() {
    assertThat(AccessTokens.resolve(null, "cookie-token"))
        .isEqualTo(new AccessToken("cookie-token"));
    assertThat(AccessTokens.resolve("Basic abc", "cookie-token"))
        .isEqualTo(new AccessToken("cookie-token"));
  }

  @Test
  void fallsBackToTheCookieWhenTheBearerHeaderIsBlank() {
    assertThat(AccessTokens.resolve("Bearer ", "cookie-token"))
        .isEqualTo(new AccessToken("cookie-token"));
  }

  @Test
  void returnsNullWhenNeitherCarriesAToken() {
    assertThat(AccessTokens.resolve(null, null)).isNull();
    assertThat(AccessTokens.resolve("Bearer ", " ")).isNull();
  }
}
