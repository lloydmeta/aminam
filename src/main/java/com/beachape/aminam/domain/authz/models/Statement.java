package com.beachape.aminam.domain.authz.models;

import com.beachape.aminam.domain.orgs.models.MembershipId;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/// The atom of a policy: an effect over a set of actions and resource patterns.
/// * memberships is used only on policies attached to resources (the memberships it trusts); naming
///   a membership grants the user only while they act under and the grant dies when the membership
///   is removed.
/// * condition is an/ optional CEL predicate evaluated against server-resolved attributes.
public record Statement(
    Effect effect,
    Set<MembershipId> memberships,
    Set<Action> actions,
    Set<ResourcePattern> resources,
    @Nullable String condition) {}
