package com.beachape.aminam.app.authc;

import com.beachape.aminam.domain.authc.models.AccessToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.NewCookie;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/// Builds the auth cookie (HttpOnly, SameSite=Strict; Secure outside dev).
@ApplicationScoped
public class AuthCookies {

  private final boolean secure;
  private final int maxAgeSeconds;

  @Inject
  AuthCookies(
      @ConfigProperty(name = "aminam.auth.cookie-secure") boolean secure,
      @ConfigProperty(name = "aminam.jwt.lifespan-seconds") int lifespanSeconds) {
    this.secure = secure;
    this.maxAgeSeconds = lifespanSeconds;
  }

  /// A session cookie carrying the token, expiring with the token.
  public NewCookie session(AccessToken token) {
    return base(token.value()).maxAge(maxAgeSeconds).build();
  }

  public NewCookie cleared() {
    return base("").maxAge(0).build();
  }

  private NewCookie.Builder base(String value) {
    return new NewCookie.Builder(AccessTokens.COOKIE_NAME)
        .value(value)
        .path("/")
        .httpOnly(true)
        .secure(secure)
        .sameSite(NewCookie.SameSite.STRICT);
  }
}
