package com.beachape.aminam.app.authc;

import com.beachape.aminam.domain.authc.services.TokenService;
import com.beachape.aminam.domain.authc.services.TokenService.ExpiredTokenException;
import com.beachape.aminam.domain.authc.services.TokenService.InvalidTokenException;
import com.beachape.aminam.domain.authc.services.TokenService.RevokedTokenException;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.IdentityProvider;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class JwtIdentityProvider implements IdentityProvider<JwtAuthenticationRequest> {

  private final TokenService tokens;

  @Inject
  JwtIdentityProvider(TokenService tokens) {
    this.tokens = tokens;
  }

  @Override
  public Class<JwtAuthenticationRequest> getRequestType() {
    return JwtAuthenticationRequest.class;
  }

  @Override
  public Uni<SecurityIdentity> authenticate(
      JwtAuthenticationRequest request, AuthenticationRequestContext context) {
    return context.runBlocking(
        () -> {
          try {
            var principal = tokens.authenticate(request.token());
            return QuarkusSecurityIdentity.builder().setPrincipal(principal).build();
          } catch (InvalidTokenException | ExpiredTokenException | RevokedTokenException e) {
            throw new AuthenticationFailedException(e.getMessage(), e);
          }
        });
  }
}
