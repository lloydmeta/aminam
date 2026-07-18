package com.beachape.aminam.app.routes.v1.authz;

import com.beachape.aminam.app.authc.Principals;
import com.beachape.aminam.app.routes.v1.authz.models.CheckRequest;
import com.beachape.aminam.app.routes.v1.authz.models.CheckResponse;
import com.beachape.aminam.domain.authz.services.AuthorisationService;
import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;

/// Policy Decision Point: batch permission checks for the caller, mounted by V1Resource at /authz.
@ApplicationScoped
@RunOnVirtualThread
@Authenticated
public class AuthzResource {

  @Inject AuthorisationService authz;

  @POST
  @Path("_check")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CheckResponse check(@Valid CheckRequest request, @Context SecurityContext security) {
    var user = Principals.requireUser(security);
    return CheckResponse.from(authz.checkAll(user, request.toDomain()));
  }
}
