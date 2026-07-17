package com.beachape.aminam.domain.authc.models;

import com.beachape.aminam.domain.orgs.models.Membership.UserMembership;
import com.beachape.aminam.domain.orgs.models.OrgId;
import java.security.Principal;
import org.jspecify.annotations.Nullable;

/// activeMembership is null for an org-less session and otherwise carries both the active
/// membership and its org, so a session can never have one without the other.
public record AuthenticatedUser(
    UserId id, String username, @Nullable UserMembership activeMembership) implements Principal {

  public AuthenticatedUser(UserId id, String username) {
    this(id, username, null);
  }

  public @Nullable OrgId activeOrg() {
    return activeMembership == null ? null : activeMembership.orgId();
  }

  @Override
  public String getName() {
    return username;
  }
}
