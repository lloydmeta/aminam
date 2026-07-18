package com.beachape.aminam.infra.authz.services;

import com.beachape.aminam.domain.authz.models.ConditionAttributes;
import com.beachape.aminam.domain.authz.models.EvaluationContext;
import com.beachape.aminam.domain.authz.models.ResourceRef;
import com.beachape.aminam.domain.authz.services.ConditionEvaluator;
import dev.cel.common.CelValidationException;
import dev.cel.compiler.CelCompiler;
import dev.cel.runtime.CelEvaluationException;
import dev.cel.runtime.CelRuntime;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.Map;

/// Evaluates a statement's CEL condition against the gathered context. The `principal` and
/// `resource` maps are built only from server-resolved facts, so a request can never influence a
/// condition. Absent attributes (org-less session, a create, a non-database resource) are left out
/// of the map, so referencing them makes the condition false. Any compile or evaluation failure
/// fails closed (false), which also covers a stored condition that no longer type-checks.
@ApplicationScoped
class CelConditionEvaluator implements ConditionEvaluator {

  private final CelCompiler compiler = CelPolicyEnvironment.compiler();
  private final CelRuntime runtime = CelPolicyEnvironment.runtime();

  @Override
  public boolean satisfied(String condition, EvaluationContext ctx) {
    try {
      var ast = compiler.compile(condition).getAst();
      var result = runtime.createProgram(ast).eval(activation(ctx));
      return result instanceof Boolean allowed && allowed;
    } catch (CelValidationException | CelEvaluationException e) {
      return false;
    }
  }

  private static Map<String, Object> activation(EvaluationContext ctx) {
    var principal = new HashMap<String, String>();
    principal.put(ConditionAttributes.ID, ctx.principal().id().value().toString());
    var active = ctx.principal().activeMembership();
    if (active != null) {
      principal.put(ConditionAttributes.ACTIVE_ORG, active.orgId().value().toString());
      principal.put(ConditionAttributes.MEMBERSHIP_ID, active.id().value().toString());
    }
    var resource = new HashMap<String, String>();
    // Per-type free-form facts a fact source contributes (e.g. a database's name as resource.name)
    // go in first; the server-resolved keys below then overwrite
    resource.putAll(ctx.resourceFacts().attributes());
    resource.put(ConditionAttributes.TYPE, ctx.resource().type().name());
    if (ctx.resource() instanceof ResourceRef.Existing existing) {
      resource.put(ConditionAttributes.ID, existing.id().toString());
    }
    resource.put(ConditionAttributes.ORG_ID, ctx.resourceFacts().owningOrg().value().toString());
    return Map.of(ConditionAttributes.PRINCIPAL, principal, ConditionAttributes.RESOURCE, resource);
  }
}
