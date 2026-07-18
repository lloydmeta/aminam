package com.beachape.aminam.app.routes.v1.authc;

import com.beachape.aminam.app.authc.AuthCookies;
import com.beachape.aminam.app.routes.v1.authc.models.LoginRequest;
import com.beachape.aminam.app.routes.v1.authc.models.LoginResponse;
import com.beachape.aminam.domain.authc.services.AuthenticationService;
import com.beachape.aminam.domain.authc.services.AuthenticationService.InvalidCredentialsException;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.RestResponse.ResponseBuilder;

@ApplicationScoped
@RunOnVirtualThread
public class LoginResource {

  @Inject AuthenticationService auth;
  @Inject AuthCookies cookies;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public RestResponse<LoginResponse> login(@Valid LoginRequest request)
      throws InvalidCredentialsException {
    var token = auth.login(request.username(), request.password());
    return ResponseBuilder.ok(new LoginResponse(token.value()))
        .cookie(cookies.session(token))
        .build();
  }
}
