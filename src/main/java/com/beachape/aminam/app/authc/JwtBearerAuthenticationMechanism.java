package com.beachape.aminam.app.authc;

import com.beachape.aminam.domain.authc.models.AccessToken;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.AuthenticationRequest;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/// Authenticates an access token from the `Authorization: Bearer` header, falling back to the
/// `access_token` cookie, so the API (bearer) and browser (cookie) share one path. Returns no
/// identity when neither is present, letting another mechanism try.
@Priority(1)
@ApplicationScoped
public class JwtBearerAuthenticationMechanism implements HttpAuthenticationMechanism {

  @Override
  public Uni<SecurityIdentity> authenticate(
      RoutingContext context, IdentityProviderManager identityProviderManager) {
    var token = extractToken(context);
    if (token == null) {
      return Uni.createFrom().nullItem();
    }
    return identityProviderManager.authenticate(new JwtAuthenticationRequest(token));
  }

  @Override
  public Uni<ChallengeData> getChallenge(RoutingContext context) {
    return Uni.createFrom()
        .item(
            new ChallengeData(
                Response.Status.UNAUTHORIZED.getStatusCode(),
                HttpHeaders.WWW_AUTHENTICATE,
                "Bearer realm=\"aminam\""));
  }

  @Override
  public Set<Class<? extends AuthenticationRequest>> getCredentialTypes() {
    return Set.of(JwtAuthenticationRequest.class);
  }

  private static @Nullable AccessToken extractToken(RoutingContext context) {
    var cookie = context.request().getCookie(AccessTokens.COOKIE_NAME);
    return AccessTokens.resolve(
        context.request().getHeader(HttpHeaders.AUTHORIZATION),
        cookie == null ? null : cookie.getValue());
  }
}
