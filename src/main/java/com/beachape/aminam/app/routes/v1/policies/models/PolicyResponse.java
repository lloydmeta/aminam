package com.beachape.aminam.app.routes.v1.policies.models;

import com.beachape.aminam.domain.authz.models.Policy;
import java.util.List;

public record PolicyResponse(
    String id, String orgId, String name, List<PolicyStatement> statements, String createdAt) {

  public static PolicyResponse from(Policy policy) {
    return new PolicyResponse(
        policy.id().asText(),
        policy.orgId().toString(),
        policy.name(),
        policy.document().statements().stream().map(PolicyStatement::from).toList(),
        policy.createdAt().toString());
  }
}
