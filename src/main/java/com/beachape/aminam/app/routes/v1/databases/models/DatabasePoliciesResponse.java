package com.beachape.aminam.app.routes.v1.databases.models;

import com.beachape.aminam.domain.authz.models.PolicyId;
import java.util.List;

public record DatabasePoliciesResponse(List<String> values) {

  public static DatabasePoliciesResponse from(List<PolicyId> policyIds) {
    return new DatabasePoliciesResponse(policyIds.stream().map(PolicyId::asText).toList());
  }
}
