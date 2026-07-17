package com.beachape.aminam.domain.authz.models;

/// A single administrable action, composed as (resource type x verb), e.g. database:update.
public record Action(ResourceType type, Verb verb) {}
