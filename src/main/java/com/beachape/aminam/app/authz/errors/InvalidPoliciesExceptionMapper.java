package com.beachape.aminam.app.authz.errors;

import com.beachape.aminam.app.authz.errors.PolicyErrorsResponse.PolicyErrorItem;
import com.beachape.aminam.domain.authz.services.PolicyAuthzService.InvalidPoliciesException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class InvalidPoliciesExceptionMapper implements ExceptionMapper<InvalidPoliciesException> {

  @Override
  public Response toResponse(InvalidPoliciesException exception) {
    var errors =
        exception.failures().stream()
            .map(failure -> new PolicyErrorItem(failure.policyId().asText(), failure.reason()))
            .toList();
    return Response.status(Response.Status.BAD_REQUEST)
        .entity(new PolicyErrorsResponse("invalid policies", errors))
        .build();
  }
}
