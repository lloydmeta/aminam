package com.beachape.aminam.domain.authz.repositories;

import com.beachape.aminam.domain.authz.models.ResourceFacts;
import com.beachape.aminam.domain.authz.models.ResourceType;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Resolves an existing resource of one or more types to its authorisation facts (its owning org
/// today, CEL attributes later). One implementation per owning feature, so authz does not import
/// other features' repositories. A `ResourceRef.ToCreate` never reaches a source: its owning org
/// comes from the request path, not a lookup.
public interface ResourceFactSource {

  /// The types this source resolves. Disjoint across sources; a duplicate is a wiring error.
  Set<ResourceType> types();

  /// The resource's facts, or null if it does not exist (a gather-level deny, mapped to 404).
  @Nullable ResourceFacts resolve(UUID id);
}
