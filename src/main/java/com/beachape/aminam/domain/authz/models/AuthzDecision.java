package com.beachape.aminam.domain.authz.models;

/// The engine's output. Binary on purpose: enforcement is binary, and abstain/not-applicable ->
/// Deny inside the engine (fail-closed) so the safe default can't be dropped at a call site.
public sealed interface AuthzDecision permits AuthzDecision.Allow, AuthzDecision.Deny {

  record Allow(String reason) implements AuthzDecision {}

  record Deny(String reason) implements AuthzDecision {}

  default boolean allowed() {
    return this instanceof Allow;
  }
}
