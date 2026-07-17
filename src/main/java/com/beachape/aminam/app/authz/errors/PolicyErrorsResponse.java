package com.beachape.aminam.app.authz.errors;

import java.util.List;

public record PolicyErrorsResponse(String message, List<PolicyErrorItem> errors) {

  public record PolicyErrorItem(String policyId, String reason) {}
}
