package com.beachape.aminam.domain.orgs.models;

import com.beachape.aminam.domain.authc.models.UserId;
import java.time.Instant;

/// The identity anchor for a session: a principal's membership in an org.
public record Membership(MembershipId id, UserId userId, OrgId orgId, Instant createdAt) {

  public UserMembership withoutUserId() {
    return new UserMembership(id, orgId);
  }

  /// The (membership, org) pair without the user id, meant to be used when
  /// the user data is already present
  public record UserMembership(MembershipId id, OrgId orgId) {}
}
