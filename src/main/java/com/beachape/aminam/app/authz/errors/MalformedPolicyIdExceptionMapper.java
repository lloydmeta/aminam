package com.beachape.aminam.app.authz.errors;

import com.beachape.aminam.app.models.ErrorResponse;
import com.beachape.aminam.domain.authz.models.PolicyId.MalformedPolicyIdException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class MalformedPolicyIdExceptionMapper
    implements ExceptionMapper<MalformedPolicyIdException> {

  @Override
  public Response toResponse(MalformedPolicyIdException exception) {
    return Response.status(Response.Status.BAD_REQUEST)
        .entity(new ErrorResponse(exception.getMessage()))
        .build();
  }
}
