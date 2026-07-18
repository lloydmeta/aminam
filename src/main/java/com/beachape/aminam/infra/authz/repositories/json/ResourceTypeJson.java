package com.beachape.aminam.infra.authz.repositories.json;

import com.beachape.aminam.domain.authz.models.ResourceType;

enum ResourceTypeJson {
  ORG,
  DATABASE,
  MEMBERSHIP,
  SELF_MEMBERSHIP,
  POLICY;

  ResourceType toDomain() {
    return switch (this) {
      case ORG -> ResourceType.ORG;
      case DATABASE -> ResourceType.DATABASE;
      case MEMBERSHIP -> ResourceType.MEMBERSHIP;
      case SELF_MEMBERSHIP -> ResourceType.SELF_MEMBERSHIP;
      case POLICY -> ResourceType.POLICY;
    };
  }

  static ResourceTypeJson from(ResourceType type) {
    return switch (type) {
      case ORG -> ORG;
      case DATABASE -> DATABASE;
      case MEMBERSHIP -> MEMBERSHIP;
      case SELF_MEMBERSHIP -> SELF_MEMBERSHIP;
      case POLICY -> POLICY;
    };
  }
}
