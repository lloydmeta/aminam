package com.beachape.aminam.app.errors;

import com.beachape.aminam.app.models.ErrorResponse;
import io.quarkus.security.ForbiddenException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {

  @Override
  public Response toResponse(ForbiddenException exception) {
    return Response.status(Response.Status.FORBIDDEN)
        .entity(new ErrorResponse("access denied"))
        .build();
  }
}
