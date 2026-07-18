package com.beachape.aminam.domain.authz.models;

import java.util.UUID;

/// The thing a policy is attached to: a typed reference to a membership or a database.
public record AttachmentPoint(AttachmentType type, UUID id) {}
