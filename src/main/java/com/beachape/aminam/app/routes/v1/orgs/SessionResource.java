package com.beachape.aminam.app.routes.v1.orgs;

import static java.util.UUID.fromString;

import com.beachape.aminam.app.authc.AuthCookies;
import com.beachape.aminam.app.authc.Principals;
import com.beachape.aminam.app.routes.v1.authc.models.LoginResponse;
import com.beachape.aminam.app.routes.v1.orgs.models.SwitchOrgRequest;
import com.beachape.aminam.domain.errors.NotFoundException;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.beachape.aminam.domain.orgs.services.OrganisationService;
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
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.RestResponse.ResponseBuilder;

@ApplicationScoped
@RunOnVirtualThread
@Authenticated
public class SessionResource {

  @Inject OrganisationService organisations;
  @Inject AuthCookies cookies;

  @POST
  @Path("switch-org")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public RestResponse<LoginResponse> switchOrg(
      @Valid SwitchOrgRequest request, @Context SecurityContext security) throws NotFoundException {
    var user = Principals.requireUser(security);
    var token = organisations.switchOrg(user, new OrgId(fromString(request.org())));
    return ResponseBuilder.ok(new LoginResponse(token.value()))
        .cookie(cookies.session(token))
        .build();
  }
}
