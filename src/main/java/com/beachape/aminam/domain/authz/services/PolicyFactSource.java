package com.beachape.aminam.domain.authz.services;

import com.beachape.aminam.domain.authz.models.PolicyId.CustomPolicyId;
import com.beachape.aminam.domain.authz.models.ResourceFacts;
import com.beachape.aminam.domain.authz.models.ResourceType;
import com.beachape.aminam.domain.authz.repositories.PolicyRepository;
import com.beachape.aminam.domain.authz.repositories.ResourceFactSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Resolves a custom policy to its facts. A policy is owned by its org, which is what confines a
/// cross-org actor when the policy is the target of a check (e.g. policy:read on assignment).
@ApplicationScoped
public class PolicyFactSource implements ResourceFactSource {

  private final PolicyRepository policies;

  @Inject
  PolicyFactSource(PolicyRepository policies) {
    this.policies = policies;
  }

  @Override
  public Set<ResourceType> types() {
    return Set.of(ResourceType.POLICY);
  }

  @Override
  public @Nullable ResourceFacts resolve(UUID id) {
    var policy = policies.findById(new CustomPolicyId(id));
    return policy == null ? null : new ResourceFacts(policy.orgId());
  }
}
