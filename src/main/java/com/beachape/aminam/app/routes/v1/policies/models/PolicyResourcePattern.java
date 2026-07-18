package com.beachape.aminam.app.routes.v1.policies.models;

import com.beachape.aminam.domain.authz.models.ResourcePattern;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record PolicyResourcePattern(@NotNull PolicyResourceType type, @Nullable UUID id) {

  ResourcePattern toDomain() {
    return new ResourcePattern(type.toDomain(), id);
  }

  static PolicyResourcePattern from(ResourcePattern pattern) {
    return new PolicyResourcePattern(PolicyResourceType.from(pattern.type()), pattern.id());
  }
}
