package com.beachape.aminam.app.routes.v1.authc;

import com.beachape.aminam.app.routes.v1.authc.models.SignupRequest;
import com.beachape.aminam.app.routes.v1.authc.models.SignupResponse;
import com.beachape.aminam.domain.authc.services.AuthenticationService;
import com.beachape.aminam.domain.authc.services.AuthenticationService.PasswordTooLongException;
import com.beachape.aminam.domain.authc.services.AuthenticationService.UsernameTakenException;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.resteasy.reactive.ResponseStatus;

@ApplicationScoped
@RunOnVirtualThread
public class SignupResource {

  @Inject AuthenticationService auth;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ResponseStatus(201)
  @APIResponse(responseCode = "201")
  public SignupResponse signup(@Valid SignupRequest request)
      throws PasswordTooLongException, UsernameTakenException {
    var user = auth.signup(request.username(), request.password());
    return new SignupResponse(user.id().value().toString(), user.username());
  }
}
