package com.beachape.aminam.domain.authz.models;

import java.util.List;

/// The frozen input to the pure decide phase. Everything here is gathered (I/O) up front; decide
/// reads only this and performs no further lookups. The regime is not stored: decide derives it
/// from the active org and the resource's owning org.
public record EvaluationContext(
    AuthzPrincipal principal,
    Action action,
    ResourceRef resource,
    ResourceFacts resourceFacts,
    List<PolicyDocument> identityPolicies,
    List<PolicyDocument> resourcePolicies) {}
