package com.beachape.aminam.domain.authz.services;

import com.beachape.aminam.domain.authz.models.AuthzDecision;
import com.beachape.aminam.domain.authz.models.EvaluationContext;

/// The pure decide phase: deterministic, side-effect-free, swappable for an external
/// implementation-based engine (OPA/Zanzibar)
@FunctionalInterface
public interface PolicyEngine {
  AuthzDecision decide(EvaluationContext ctx);
}
