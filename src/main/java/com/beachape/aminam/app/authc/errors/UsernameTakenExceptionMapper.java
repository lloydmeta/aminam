package com.beachape.aminam.app.authc.errors;

import com.beachape.aminam.app.models.ErrorResponse;
import com.beachape.aminam.domain.authc.services.AuthenticationService.UsernameTakenException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class UsernameTakenExceptionMapper implements ExceptionMapper<UsernameTakenException> {

  @Override
  public Response toResponse(UsernameTakenException exception) {
    return Response.status(Response.Status.CONFLICT)
        .entity(new ErrorResponse("username already taken"))
        .build();
  }
}
