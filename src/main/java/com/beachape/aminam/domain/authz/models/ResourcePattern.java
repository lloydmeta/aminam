package com.beachape.aminam.domain.authz.models;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// A resource matcher in a statement: a type and either a concrete id or a wildcard (null id).
public record ResourcePattern(ResourceType type, @Nullable UUID id) {

  /// All resources of a type, e.g. database:*.
  public static ResourcePattern wildcard(ResourceType type) {
    return new ResourcePattern(type, null);
  }

  /// Whether this pattern covers the given resource. A `ToCreate` has no id yet, so only a wildcard
  /// pattern can match it.
  public boolean matches(ResourceRef ref) {
    return switch (ref) {
      case ResourceRef.Existing e -> type == e.type() && (id == null || id.equals(e.id()));
      case ResourceRef.ToCreate c -> type == c.type() && id == null;
    };
  }
}
