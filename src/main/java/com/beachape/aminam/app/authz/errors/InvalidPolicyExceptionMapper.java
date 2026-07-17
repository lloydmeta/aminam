package com.beachape.aminam.app.authz.errors;

import com.beachape.aminam.app.authz.errors.PolicyValidationErrorsResponse.PolicyValidationError;
import com.beachape.aminam.domain.authz.services.PolicyValidator.InvalidPolicyException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidPolicyExceptionMapper implements ExceptionMapper<InvalidPolicyException> {

  @Override
  public Response toResponse(InvalidPolicyException exception) {
    var errors =
        exception.failures().stream()
            .map(failure -> new PolicyValidationError(failure.location(), failure.reason()))
            .toList();
    return Response.status(Response.Status.BAD_REQUEST)
        .entity(new PolicyValidationErrorsResponse("invalid policy", errors))
        .build();
  }
}
