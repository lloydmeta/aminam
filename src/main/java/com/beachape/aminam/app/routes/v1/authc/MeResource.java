package com.beachape.aminam.app.routes.v1.authc;

import com.beachape.aminam.app.authc.Principals;
import com.beachape.aminam.app.routes.v1.authc.models.MeResponse;
import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;

@ApplicationScoped
@RunOnVirtualThread
public class MeResource {

  @GET
  @Authenticated
  @Produces(MediaType.APPLICATION_JSON)
  public MeResponse me(@Context SecurityContext security) {
    var user = Principals.requireUser(security);
    var org = user.activeOrg();
    return new MeResponse(
        user.id().value().toString(), user.username(), org == null ? null : org.value().toString());
  }
}
