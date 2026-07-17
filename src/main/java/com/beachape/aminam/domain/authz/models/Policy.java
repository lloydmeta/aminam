package com.beachape.aminam.domain.authz.models;

import com.beachape.aminam.domain.authz.models.PolicyId.CustomPolicyId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import java.time.Instant;

/// A custom policy: an org-owned, named document. System policies are code; these are data.
public record Policy(
    CustomPolicyId id, OrgId orgId, String name, PolicyDocument document, Instant createdAt) {}
