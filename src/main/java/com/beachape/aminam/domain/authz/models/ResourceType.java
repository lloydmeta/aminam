package com.beachape.aminam.domain.authz.models;

/// The kinds of thing authorisation is decided over. POLICY is a custom policy document.
/// SELF_MEMBERSHIP is the actor's own seat in an org: a type every member can act on, so leaving is
/// `self_membership:delete` (no instance targeting, no bespoke verb).
public enum ResourceType {
  ORG,
  DATABASE,
  MEMBERSHIP,
  SELF_MEMBERSHIP,
  POLICY
}
