package com.beachape.aminam.app.routes.v1.authz.models;

import com.beachape.aminam.domain.authz.models.ResourceType;

public enum AuthzResourceType {
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
}
