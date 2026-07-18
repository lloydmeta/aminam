package com.beachape.aminam.domain.orgs.services;

import com.beachape.aminam.domain.authz.models.ResourceFacts;
import com.beachape.aminam.domain.authz.models.ResourceType;
import com.beachape.aminam.domain.authz.repositories.ResourceFactSource;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import com.beachape.aminam.domain.orgs.repositories.MembershipRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Resolves a membership resource to its facts. A membership is owned by its org. SELF_MEMBERSHIP
/// resolves the same way; the "self" constraint is enforced by the endpoint shape, not the fact.
@ApplicationScoped
public class MembershipFactSource implements ResourceFactSource {

  private final MembershipRepository memberships;

  @Inject
  MembershipFactSource(MembershipRepository memberships) {
    this.memberships = memberships;
  }

  @Override
  public Set<ResourceType> types() {
    return Set.of(ResourceType.MEMBERSHIP, ResourceType.SELF_MEMBERSHIP);
  }

  @Override
  public @Nullable ResourceFacts resolve(UUID id) {
    var membership = memberships.findById(new MembershipId(id));
    return membership == null ? null : new ResourceFacts(membership.orgId());
  }
}
