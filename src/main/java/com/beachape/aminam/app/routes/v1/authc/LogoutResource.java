package com.beachape.aminam.app.routes.v1.authc;

import com.beachape.aminam.app.authc.AccessTokens;
import com.beachape.aminam.app.authc.AuthCookies;
import com.beachape.aminam.app.models.EmptyResponse;
import com.beachape.aminam.domain.authc.services.TokenService;
import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.RestResponse.ResponseBuilder;
import org.jspecify.annotations.Nullable;

@ApplicationScoped
@RunOnVirtualThread
public class LogoutResource {

  @Inject TokenService tokens;
  @Inject AuthCookies cookies;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @Authenticated
  public RestResponse<EmptyResponse> logout(
      @HeaderParam(HttpHeaders.AUTHORIZATION) @Nullable String authorization,
      @CookieParam(AccessTokens.COOKIE_NAME) @Nullable String cookieToken) {
    var token = AccessTokens.resolve(authorization, cookieToken);
    if (token != null) {
      tokens.revoke(token);
    }
    return ResponseBuilder.ok(new EmptyResponse()).cookie(cookies.cleared()).build();
  }
}
