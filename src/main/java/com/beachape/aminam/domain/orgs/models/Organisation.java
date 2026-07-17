package com.beachape.aminam.domain.orgs.models;

import com.beachape.aminam.domain.authc.models.UserId;
import java.time.Instant;

public record Organisation(OrgId id, String name, UserId createdBy, Instant createdAt) {}
