package com.beachape.aminam.domain.authz.models;

import com.beachape.aminam.domain.orgs.models.OrgId;
import java.util.UUID;

/// A resource a request targets. Either an `Existing` resource (its owning org is resolved by an id
/// lookup) or one `ToCreate` within an org (no row yet, so the owning org comes from the request
/// path). A create can only ever match a wildcard pattern, never a pinned id.
public sealed interface ResourceRef {

  ResourceType type();

  record Existing(ResourceType type, UUID id) implements ResourceRef {}

  record ToCreate(ResourceType type, OrgId owningOrg) implements ResourceRef {}
}
