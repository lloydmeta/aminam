package com.beachape.aminam.app.errors;

import com.beachape.aminam.app.models.ErrorResponse;
import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
class UnauthorizedExceptionMapper implements ExceptionMapper<UnauthorizedException> {

  @Override
  public Response toResponse(UnauthorizedException exception) {
    return Response.status(Response.Status.UNAUTHORIZED)
        .entity(new ErrorResponse("authentication required"))
        .build();
  }
}
