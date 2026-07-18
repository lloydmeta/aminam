package com.beachape.aminam.app.authc.errors;

import com.beachape.aminam.app.models.ErrorResponse;
import com.beachape.aminam.domain.authc.services.AuthenticationService.InvalidCredentialsException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidCredentialsExceptionMapper
    implements ExceptionMapper<InvalidCredentialsException> {

  @Override
  public Response toResponse(InvalidCredentialsException exception) {
    return Response.status(Response.Status.UNAUTHORIZED)
        .entity(new ErrorResponse("invalid username or credentials"))
        .build();
  }
}
