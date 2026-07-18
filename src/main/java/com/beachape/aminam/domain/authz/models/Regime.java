package com.beachape.aminam.domain.authz.models;

/// Whether a request acts within the active org or across an org boundary. Derived by the engine
/// from the active org and the resource's owning org, never stored.
public enum Regime {
  INTERNAL,
  CROSS_ORG
}
