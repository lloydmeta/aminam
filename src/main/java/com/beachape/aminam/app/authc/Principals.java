package com.beachape.aminam.app.authc;

import com.beachape.aminam.domain.authc.models.AuthenticatedUser;
import io.quarkus.security.ForbiddenException;
import jakarta.ws.rs.core.SecurityContext;

public final class Principals {

  private Principals() {}

  public static AuthenticatedUser requireUser(SecurityContext security) {
    if (security.getUserPrincipal() instanceof AuthenticatedUser user) {
      return user;
    }
    throw new ForbiddenException("not available to this principal");
  }
}
