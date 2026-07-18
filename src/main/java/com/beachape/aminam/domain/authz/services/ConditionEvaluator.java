package com.beachape.aminam.domain.authz.services;

import com.beachape.aminam.domain.authz.models.EvaluationContext;

/// Tests a statement's CEL condition against the gathered context. Taking the whole context keeps
/// the signature stable as the available attributes grow. The CEL implementation lives in infra.
@FunctionalInterface
public interface ConditionEvaluator {
  boolean satisfied(String condition, EvaluationContext ctx);
}
