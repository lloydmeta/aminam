package com.beachape.aminam.app.errors;

import com.beachape.aminam.app.models.ErrorResponse;
import io.quarkus.security.AuthenticationFailedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
class AuthenticationFailedExceptionMapper
    implements ExceptionMapper<AuthenticationFailedException> {

  @Override
  public Response toResponse(AuthenticationFailedException exception) {
    return Response.status(Response.Status.UNAUTHORIZED)
        .entity(new ErrorResponse("invalid or expired token"))
        .build();
  }
}
