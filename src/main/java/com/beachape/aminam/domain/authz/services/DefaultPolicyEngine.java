package com.beachape.aminam.domain.authz.services;

import com.beachape.aminam.domain.authz.models.AuthzDecision;
import com.beachape.aminam.domain.authz.models.AuthzDecision.Allow;
import com.beachape.aminam.domain.authz.models.AuthzDecision.Deny;
import com.beachape.aminam.domain.authz.models.Effect;
import com.beachape.aminam.domain.authz.models.EvaluationContext;
import com.beachape.aminam.domain.authz.models.PolicyDocument;
import com.beachape.aminam.domain.authz.models.Regime;
import com.beachape.aminam.domain.authz.models.Statement;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Objects;

/// The pure decide phase: no I/O, reads only the frozen EvaluationContext.
///
/// Decision order:
/// 1. Explicit DENY in any policy -> Deny (DENY always wins, regardless of regime).
/// 2. ALLOW check is regime-dependent:
///    - INTERNAL (active org == resource's owning org): identity OR resource permit?
///    - CROSS_ORG: identity AND resource permit both required (bilateral consent).
/// 3. No matching permit -> Deny (fail-closed).
@ApplicationScoped
public class DefaultPolicyEngine implements PolicyEngine {

  private final ConditionEvaluator conditions;

  @Inject
  DefaultPolicyEngine(ConditionEvaluator conditions) {
    this.conditions = conditions;
  }

  @Override
  public AuthzDecision decide(EvaluationContext ctx) {
    var regime =
        Objects.equals(ctx.principal().activeOrg(), ctx.resourceFacts().owningOrg())
            ? Regime.INTERNAL
            : Regime.CROSS_ORG;

    // identityPolicies are attached to the active membership; resourcePolicies are attached to the
    // target database (empty for every other resource type). A matched DENY on either side wins.
    if (anyMatch(ctx.identityPolicies(), ctx, Effect.DENY, /* resourcePolicy= */ false)
        || anyMatch(ctx.resourcePolicies(), ctx, Effect.DENY, /* resourcePolicy= */ true)) {
      return new Deny("explicit deny (" + regime + ")");
    }

    // idPermit from the active membership's policies; resPermit from the database's resource
    // policies, matched against the active membership (so it stays false for non-database targets).
    boolean idPermit =
        anyMatch(ctx.identityPolicies(), ctx, Effect.ALLOW, /* resourcePolicy= */ false);
    boolean resPermit =
        anyMatch(ctx.resourcePolicies(), ctx, Effect.ALLOW, /* resourcePolicy= */ true);

    boolean allow =
        switch (regime) {
          case INTERNAL -> idPermit || resPermit;
          case CROSS_ORG -> idPermit && resPermit;
        };
    return allow ? new Allow(regime + " permit") : new Deny("no matching permit (" + regime + ")");
  }

  private boolean anyMatch(
      List<PolicyDocument> policies, EvaluationContext ctx, Effect effect, boolean resourcePolicy) {
    for (var policy : policies) {
      for (var statement : policy.statements()) {
        if (statement.effect() == effect && matches(statement, ctx, resourcePolicy)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean matches(Statement statement, EvaluationContext ctx, boolean resourcePolicy) {
    if (!statement.actions().contains(ctx.action())) {
      return false;
    }
    if (statement.resources().stream().noneMatch(pattern -> pattern.matches(ctx.resource()))) {
      return false;
    }
    // Identity policies are pre-scoped to the requesting user: the gather phase only loads
    // policies attached to their active-org membership, so no per-statement principal check
    // is needed. Resource policies are attached to the resource and can name several memberships,
    // so we confirm the requester's active membership is one of them. Trust is membership-scoped:
    // an org-less session has no active membership and can never match a resource policy.
    if (resourcePolicy) {
      var active = ctx.principal().activeMembership();
      if (active == null || !statement.memberships().contains(active.id())) {
        return false;
      }
    }
    var condition = statement.condition();
    return condition == null || conditions.satisfied(condition, ctx);
  }
}
