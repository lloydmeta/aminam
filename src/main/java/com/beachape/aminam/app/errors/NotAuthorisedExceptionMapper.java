package com.beachape.aminam.app.errors;

import com.beachape.aminam.app.models.ErrorResponse;
import com.beachape.aminam.domain.errors.NotAuthorisedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class NotAuthorisedExceptionMapper implements ExceptionMapper<NotAuthorisedException> {

  @Override
  public Response toResponse(NotAuthorisedException exception) {
    return Response.status(Response.Status.FORBIDDEN)
        .entity(new ErrorResponse("not authorised"))
        .build();
  }
}
