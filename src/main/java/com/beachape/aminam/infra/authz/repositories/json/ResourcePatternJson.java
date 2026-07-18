package com.beachape.aminam.infra.authz.repositories.json;

import com.beachape.aminam.domain.authz.models.ResourcePattern;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

record ResourcePatternJson(ResourceTypeJson type, @Nullable UUID id) {

  ResourcePattern toDomain() {
    return new ResourcePattern(type.toDomain(), id);
  }

  static ResourcePatternJson from(ResourcePattern pattern) {
    return new ResourcePatternJson(ResourceTypeJson.from(pattern.type()), pattern.id());
  }
}
