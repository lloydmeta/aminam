package com.beachape.aminam.app.authz.errors;

import java.util.List;

public record PolicyValidationErrorsResponse(String message, List<PolicyValidationError> errors) {

  public record PolicyValidationError(String location, String reason) {}
}
