package com.beachape.aminam.domain.authz.models;

import java.util.List;

/// A policy: an ordered list of statements. System policies are code; custom policies are data.
public record PolicyDocument(List<Statement> statements) {}
