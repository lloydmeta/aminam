package com.beachape.aminam.domain.databases.models;

import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import java.time.Instant;

public record Database(
    DatabaseId id, OrgId orgId, String name, UserId createdBy, Instant createdAt) {}
