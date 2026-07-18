package com.beachape.aminam.domain.authz.models;

/// A policy hung on an attachment point. The stored row's surrogate id is an infra concern.
public record PolicyAttachment(AttachmentPoint point, PolicyId policyId) {}
