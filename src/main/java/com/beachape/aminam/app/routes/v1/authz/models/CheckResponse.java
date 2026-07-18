package com.beachape.aminam.app.routes.v1.authz.models;

import com.beachape.aminam.domain.authz.models.AuthzDecision;
import java.util.List;

public record CheckResponse(boolean allOk, List<CheckResult> results) {

  public static CheckResponse from(List<AuthzDecision> decisions) {
    var results = decisions.stream().map(CheckResult::from).toList();
    return new CheckResponse(decisions.stream().allMatch(AuthzDecision::allowed), results);
  }
}
