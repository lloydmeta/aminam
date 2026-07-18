package com.beachape.aminam.app.routes.v1.policies.models;

import com.beachape.aminam.domain.authz.models.ResourceType;

public enum PolicyResourceType {
  ORG,
  DATABASE,
  MEMBERSHIP,
  SELF_MEMBERSHIP,
  POLICY;

  public ResourceType toDomain() {
    return switch (this) {
      case ORG -> ResourceType.ORG;
      case DATABASE -> ResourceType.DATABASE;
      case MEMBERSHIP -> ResourceType.MEMBERSHIP;
      case SELF_MEMBERSHIP -> ResourceType.SELF_MEMBERSHIP;
      case POLICY -> ResourceType.POLICY;
    };
  }

  public static PolicyResourceType from(ResourceType type) {
    return switch (type) {
      case ORG -> ORG;
      case DATABASE -> DATABASE;
      case MEMBERSHIP -> MEMBERSHIP;
      case SELF_MEMBERSHIP -> SELF_MEMBERSHIP;
      case POLICY -> POLICY;
    };
  }
}
