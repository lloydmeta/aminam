package com.beachape.aminam.app.errors;

import com.beachape.aminam.app.models.ErrorResponse;
import com.beachape.aminam.domain.errors.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {

  @Override
  public Response toResponse(NotFoundException exception) {
    return Response.status(Response.Status.NOT_FOUND)
        .entity(new ErrorResponse(exception.displayMessage()))
        .build();
  }
}
