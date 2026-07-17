package com.beachape.aminam.app.orgs.errors;

import com.beachape.aminam.app.models.ErrorResponse;
import com.beachape.aminam.domain.orgs.services.OrganisationService.MemberAlreadyExistsException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class MemberAlreadyExistsExceptionMapper
    implements ExceptionMapper<MemberAlreadyExistsException> {

  @Override
  public Response toResponse(MemberAlreadyExistsException exception) {
    return Response.status(Response.Status.CONFLICT)
        .entity(new ErrorResponse("already a member"))
        .build();
  }
}
