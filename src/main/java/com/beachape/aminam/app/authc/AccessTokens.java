package com.beachape.aminam.app.authc;

import com.beachape.aminam.domain.authc.models.AccessToken;
import org.jspecify.annotations.Nullable;

public final class AccessTokens {

  public static final String COOKIE_NAME = "access_token";
  private static final String BEARER_PREFIX = "Bearer ";

  private AccessTokens() {}

  public static @Nullable AccessToken resolve(
      @Nullable String authorizationHeader, @Nullable String cookieValue) {
    if (authorizationHeader != null && authorizationHeader.startsWith(BEARER_PREFIX)) {
      var value = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
      if (!value.isEmpty()) {
        return new AccessToken(value);
      }
    }
    if (cookieValue != null && !cookieValue.isBlank()) {
      return new AccessToken(cookieValue);
    }
    return null;
  }
}
