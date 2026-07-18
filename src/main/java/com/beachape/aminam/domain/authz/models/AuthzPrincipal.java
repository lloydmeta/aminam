package com.beachape.aminam.domain.authz.models;

import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.orgs.models.Membership;
import com.beachape.aminam.domain.orgs.models.OrgId;
import org.jspecify.annotations.Nullable;

/// The principal projection the engine reasons over. activeMembership is null for an org-less
/// session; otherwise it carries the active membership (trusted by resource policies) and its org
/// (which drives the regime).
public record AuthzPrincipal(UserId id, Membership.@Nullable UserMembership activeMembership) {

  public @Nullable OrgId activeOrg() {
    return activeMembership == null ? null : activeMembership.orgId();
  }
}
