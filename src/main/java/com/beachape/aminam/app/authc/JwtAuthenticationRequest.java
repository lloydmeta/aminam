package com.beachape.aminam.app.authc;

import com.beachape.aminam.domain.authc.models.AccessToken;
import io.quarkus.security.identity.request.BaseAuthenticationRequest;

public final class JwtAuthenticationRequest extends BaseAuthenticationRequest {

  private final AccessToken token;

  public JwtAuthenticationRequest(AccessToken token) {
    this.token = token;
  }

  public AccessToken token() {
    return token;
  }
}
