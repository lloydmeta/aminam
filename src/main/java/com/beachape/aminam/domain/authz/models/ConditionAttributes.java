package com.beachape.aminam.domain.authz.models;

/// The server-resolved attributes a policy condition may read.
public final class ConditionAttributes {

  /// Root objects (must be declared so a condition referencing them type-checks).
  public static final String PRINCIPAL = "principal";
  public static final String RESOURCE = "resource";

  /// Inner attribute keys (under one of the roots).
  public static final String ID = "id";
  public static final String ACTIVE_ORG = "active_org";
  public static final String MEMBERSHIP_ID = "membership_id";
  public static final String TYPE = "type";
  public static final String ORG_ID = "org_id";
  public static final String NAME = "name";
  public static final String CREATED_BY = "created_by";

  /// API-facing description of the condition field (used in the OpenAPI schema).
  public static final String CONDITION_DESCRIPTION =
      "Optional boolean CEL expression over the server-resolved `principal` and `resource` objects;"
          + " the statement matches only when it is true. Deterministic builtins only (no time"
          + " functions). Currently populated attributes: "
          + PRINCIPAL
          + "."
          + ID
          + ", "
          + PRINCIPAL
          + "."
          + ACTIVE_ORG
          + ", "
          + PRINCIPAL
          + "."
          + MEMBERSHIP_ID
          + ", "
          + RESOURCE
          + "."
          + TYPE
          + ", "
          + RESOURCE
          + "."
          + ID
          + ", "
          + RESOURCE
          + "."
          + ORG_ID
          + ", "
          + RESOURCE
          + "."
          + NAME
          + ", "
          + RESOURCE
          + "."
          + CREATED_BY
          + " (plus any resource attributes a fact source contributes). An attribute that is absent"
          + " for the request (an org-less session, a create, a non-database resource, or an unknown"
          + " key) makes the condition false.";

  private ConditionAttributes() {}
}
