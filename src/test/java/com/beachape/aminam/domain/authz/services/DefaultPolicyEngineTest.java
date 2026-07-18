package com.beachape.aminam.domain.authz.services;

import static com.beachape.aminam.domain.authz.models.ResourcePattern.wildcard;
import static com.beachape.aminam.domain.authz.models.ResourceType.DATABASE;
import static com.beachape.aminam.domain.authz.models.ResourceType.ORG;
import static com.beachape.aminam.domain.authz.models.Verb.READ;
import static com.beachape.aminam.domain.authz.models.Verb.UPDATE;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.authz.models.Action;
import com.beachape.aminam.domain.authz.models.AuthzPrincipal;
import com.beachape.aminam.domain.authz.models.Effect;
import com.beachape.aminam.domain.authz.models.EvaluationContext;
import com.beachape.aminam.domain.authz.models.PolicyDocument;
import com.beachape.aminam.domain.authz.models.ResourceFacts;
import com.beachape.aminam.domain.authz.models.ResourcePattern;
import com.beachape.aminam.domain.authz.models.ResourceRef;
import com.beachape.aminam.domain.authz.models.Statement;
import com.beachape.aminam.domain.orgs.models.Membership;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

final class DefaultPolicyEngineTest {

  // A null condition must never reach the evaluator; any condition test that does is a bug.
  private static final ConditionEvaluator THROWING =
      (condition, ctx) -> {
        throw new AssertionError("evaluator called for: " + condition);
      };

  private static final UserId USER = new UserId(randomUUID());
  private static final MembershipId MEMBER = new MembershipId(randomUUID());
  private static final OrgId ORG_A = new OrgId(randomUUID());
  private static final Membership.UserMembership ACTIVE_ORG_A =
      new Membership.UserMembership(MEMBER, ORG_A);
  private static final OrgId ORG_B = new OrgId(randomUUID());
  private static final ResourceRef ORG_A_REF = new ResourceRef.Existing(ORG, ORG_A.value());
  private static final UUID DB = randomUUID();
  private static final ResourceRef DB_REF = new ResourceRef.Existing(DATABASE, DB);
  private static final Action READ_ORG = new Action(ORG, READ);
  private static final Action READ_DB = new Action(DATABASE, READ);
  private static final Action UPDATE_DB = new Action(DATABASE, UPDATE);

  @Test
  void internalIdentityPermitAllows() {
    var ctx =
        internal(ORG_A_REF, READ_ORG, List.of(doc(allow(READ_ORG, wildcard(ORG)))), List.of());
    assertThat(engine(THROWING).decide(ctx).allowed()).isTrue();
  }

  @Test
  void uncoveredActionDefaultsToDeny() {
    var ctx =
        internal(DB_REF, UPDATE_DB, List.of(doc(allow(READ_DB, wildcard(DATABASE)))), List.of());
    assertThat(engine(THROWING).decide(ctx).allowed()).isFalse();
  }

  @Test
  void explicitDenyOverridesAnAllow() {
    var ctx =
        internal(
            DB_REF,
            UPDATE_DB,
            List.of(
                doc(allow(UPDATE_DB, wildcard(DATABASE))),
                doc(deny(UPDATE_DB, wildcard(DATABASE)))),
            List.of());
    assertThat(engine(THROWING).decide(ctx).allowed()).isFalse();
  }

  @Test
  void crossOrgDeniedWithoutResourcePolicy() {
    var ctx =
        crossOrg(DB_REF, READ_DB, List.of(doc(allow(READ_DB, wildcard(DATABASE)))), List.of());
    assertThat(engine(THROWING).decide(ctx).allowed()).isFalse();
  }

  @Test
  void crossOrgAllowedWhenBothSidesPermit() {
    var resourcePolicy = doc(allowPrincipals(READ_DB, wildcard(DATABASE), Set.of(MEMBER)));
    var ctx =
        crossOrg(
            DB_REF,
            READ_DB,
            List.of(doc(allow(READ_DB, wildcard(DATABASE)))),
            List.of(resourcePolicy));
    assertThat(engine(THROWING).decide(ctx).allowed()).isTrue();
  }

  @Test
  void internalResourcePolicyNamingPrincipalPermits() {
    var resourcePolicy = doc(allowPrincipals(READ_DB, wildcard(DATABASE), Set.of(MEMBER)));
    var ctx = internal(DB_REF, READ_DB, List.of(), List.of(resourcePolicy));
    assertThat(engine(THROWING).decide(ctx).allowed()).isTrue();
  }

  @Test
  void resourcePolicyNotNamingPrincipalAbstains() {
    var other =
        doc(allowPrincipals(READ_DB, wildcard(DATABASE), Set.of(new MembershipId(randomUUID()))));
    var ctx = internal(DB_REF, READ_DB, List.of(), List.of(other));
    assertThat(engine(THROWING).decide(ctx).allowed()).isFalse();
  }

  @Test
  void orgLessPrincipalNeverMatchesResourcePolicy() {
    var resourcePolicy = doc(allowPrincipals(READ_DB, wildcard(DATABASE), Set.of(MEMBER)));
    var ctx =
        new EvaluationContext(
            new AuthzPrincipal(USER, null),
            READ_DB,
            DB_REF,
            new ResourceFacts(ORG_A),
            List.of(doc(allow(READ_DB, wildcard(DATABASE)))),
            List.of(resourcePolicy));
    assertThat(engine(THROWING).decide(ctx).allowed()).isFalse();
  }

  @Test
  void membershipsOnAnIdentityPolicyAreIgnoredAndGrantsAsInternalRegime() {
    // A statement authored naming some OTHER membership mistakenly placed in an
    // identity policy: memberships take effect only as resource policies, so here they are ignored.
    // Naming a (foreign) membership neither widens nor blocks it.
    var foreign = new MembershipId(randomUUID());
    var misattached = doc(allowPrincipals(READ_DB, wildcard(DATABASE), Set.of(foreign)));
    var ctx = internal(DB_REF, READ_DB, List.of(misattached), List.of());
    assertThat(engine(THROWING).decide(ctx).allowed()).isTrue();
  }

  @Test
  void membershipsOnAnIdentityPolicyCannotBridgeCrossOrg() {
    // The same mis-attached share statement cannot open cross-org access: with no resource policy
    // the cross-org AND check denies, so identity-side memberships never leak across the boundary.
    var foreign = new MembershipId(randomUUID());
    var misattached = doc(allowPrincipals(READ_DB, wildcard(DATABASE), Set.of(foreign)));
    var ctx = crossOrg(DB_REF, READ_DB, List.of(misattached), List.of());
    assertThat(engine(THROWING).decide(ctx).allowed()).isFalse();
  }

  @Test
  void defaultDenyWithNoPolicies() {
    assertThat(
            engine(THROWING).decide(internal(ORG_A_REF, READ_ORG, List.of(), List.of())).allowed())
        .isFalse();
  }

  @Test
  void nullConditionNeverInvokesEvaluator() {
    // THROWING would fail the test if decide consulted the evaluator for a null-condition
    // statement.
    var ctx =
        internal(ORG_A_REF, READ_ORG, List.of(doc(allow(READ_ORG, wildcard(ORG)))), List.of());
    assertThat(engine(THROWING).decide(ctx).allowed()).isTrue();
  }

  @Test
  void conditionFalseBlocksTheMatch() {
    var ctx =
        internal(ORG_A_REF, READ_ORG, List.of(doc(allowIf(READ_ORG, wildcard(ORG)))), List.of());
    assertThat(engine((condition, c) -> false).decide(ctx).allowed()).isFalse();
  }

  @Test
  void conditionTrueAllowsTheMatch() {
    var ctx =
        internal(ORG_A_REF, READ_ORG, List.of(doc(allowIf(READ_ORG, wildcard(ORG)))), List.of());
    assertThat(engine((condition, c) -> true).decide(ctx).allowed()).isTrue();
  }

  private static DefaultPolicyEngine engine(ConditionEvaluator conditions) {
    return new DefaultPolicyEngine(conditions);
  }

  /// Returns an evaluation context for a user in ORG_A, evaluating a
  /// resource in ORG_A, with the given identity and resource policies.
  private static EvaluationContext internal(
      ResourceRef resource,
      Action action,
      List<PolicyDocument> identity,
      List<PolicyDocument> resourcePolicies) {
    return new EvaluationContext(
        new AuthzPrincipal(USER, ACTIVE_ORG_A),
        action,
        resource,
        new ResourceFacts(ORG_A),
        identity,
        resourcePolicies);
  }

  /// Returns an evaluation context for a user in ORG_A, evaluating a
  /// resource in ORG_B, with the given identity and resource policies.
  private static EvaluationContext crossOrg(
      ResourceRef resource,
      Action action,
      List<PolicyDocument> identity,
      List<PolicyDocument> resourcePolicies) {
    return new EvaluationContext(
        new AuthzPrincipal(USER, ACTIVE_ORG_A),
        action,
        resource,
        new ResourceFacts(ORG_B),
        identity,
        resourcePolicies);
  }

  private static PolicyDocument doc(Statement... statements) {
    return new PolicyDocument(List.of(statements));
  }

  private static Statement allow(Action action, ResourcePattern resource) {
    return statement(Effect.ALLOW, Set.of(), action, resource, null);
  }

  private static Statement deny(Action action, ResourcePattern resource) {
    return statement(Effect.DENY, Set.of(), action, resource, null);
  }

  private static Statement allowPrincipals(
      Action action, ResourcePattern resource, Set<MembershipId> principals) {
    return statement(Effect.ALLOW, principals, action, resource, null);
  }

  private static Statement allowIf(Action action, ResourcePattern resource) {
    return statement(
        Effect.ALLOW, Set.of(), action, resource, "resource.org_id == principal.active_org");
  }

  private static Statement statement(
      Effect effect,
      Set<MembershipId> principals,
      Action action,
      ResourcePattern resource,
      @Nullable String condition) {
    return new Statement(effect, principals, Set.of(action), Set.of(resource), condition);
  }
}
