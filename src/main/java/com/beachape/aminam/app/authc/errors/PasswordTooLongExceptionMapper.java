package com.beachape.aminam.app.authc.errors;

import com.beachape.aminam.app.models.ErrorResponse;
import com.beachape.aminam.domain.authc.services.AuthenticationService.PasswordTooLongException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class PasswordTooLongExceptionMapper implements ExceptionMapper<PasswordTooLongException> {

  @Override
  public Response toResponse(PasswordTooLongException exception) {
    return Response.status(Response.Status.BAD_REQUEST)
        .entity(new ErrorResponse("password must be at most 72 bytes"))
        .build();
  }
}
