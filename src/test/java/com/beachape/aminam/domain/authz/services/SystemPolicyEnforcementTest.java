package com.beachape.aminam.domain.authz.services;

import static com.beachape.aminam.domain.authz.models.ResourceType.DATABASE;
import static com.beachape.aminam.domain.authz.models.ResourceType.MEMBERSHIP;
import static com.beachape.aminam.domain.authz.models.ResourceType.ORG;
import static com.beachape.aminam.domain.authz.models.ResourceType.SELF_MEMBERSHIP;
import static com.beachape.aminam.domain.authz.models.Verb.CREATE;
import static com.beachape.aminam.domain.authz.models.Verb.DELETE;
import static com.beachape.aminam.domain.authz.models.Verb.READ;
import static com.beachape.aminam.domain.authz.models.Verb.UPDATE;
import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.authz.models.Action;
import com.beachape.aminam.domain.authz.models.AuthzPrincipal;
import com.beachape.aminam.domain.authz.models.EvaluationContext;
import com.beachape.aminam.domain.authz.models.PolicyId.SystemPolicyId;
import com.beachape.aminam.domain.authz.models.ResourceFacts;
import com.beachape.aminam.domain.authz.models.ResourceRef;
import com.beachape.aminam.domain.orgs.models.Membership;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import java.util.List;
import org.junit.jupiter.api.Test;

// Feeds the  policyId catalogue through the  engine so a policyId's runtime effect is
// pinned.
final class SystemPolicyEnforcementTest {

  private static final ConditionEvaluator THROWING =
      (condition, ctx) -> {
        throw new AssertionError("system policies carry no conditions; evaluator must not run");
      };
  private static final UserId USER = new UserId(randomUUID());
  private static final MembershipId MEMBER = new MembershipId(randomUUID());
  private static final OrgId ORG_ID = new OrgId(randomUUID());

  @Test
  void managerCanReadAndUpdateItsOrg() {
    assertThat(allows(SystemPolicies.MANAGER, new Action(ORG, READ))).isTrue();
    assertThat(allows(SystemPolicies.MANAGER, new Action(ORG, UPDATE))).isTrue();
  }

  @Test
  void viewerCanReadButNotUpdate() {
    assertThat(allows(SystemPolicies.VIEWER, new Action(ORG, READ))).isTrue();
    assertThat(allows(SystemPolicies.VIEWER, new Action(ORG, UPDATE))).isFalse();
  }

  @Test
  void adminCanDeleteADatabaseButNotUpdateTheOrg() {
    assertThat(allows(SystemPolicies.ADMIN, new Action(DATABASE, DELETE))).isTrue();
    assertThat(allows(SystemPolicies.ADMIN, new Action(ORG, UPDATE))).isFalse();
  }

  @Test
  void everyRoleCanSelfLeave() {
    assertThat(allows(SystemPolicies.VIEWER, new Action(SELF_MEMBERSHIP, DELETE))).isTrue();
    assertThat(allows(SystemPolicies.ADMIN, new Action(SELF_MEMBERSHIP, DELETE))).isTrue();
    assertThat(allows(SystemPolicies.MANAGER, new Action(SELF_MEMBERSHIP, DELETE))).isTrue();
  }

  @Test
  void onlyManagerManagesMembers() {
    assertThat(allows(SystemPolicies.MANAGER, new Action(MEMBERSHIP, CREATE))).isTrue();
    assertThat(allows(SystemPolicies.MANAGER, new Action(MEMBERSHIP, DELETE))).isTrue();
    assertThat(allows(SystemPolicies.VIEWER, new Action(MEMBERSHIP, CREATE))).isFalse();
    assertThat(allows(SystemPolicies.VIEWER, new Action(MEMBERSHIP, DELETE))).isFalse();
    assertThat(allows(SystemPolicies.ADMIN, new Action(MEMBERSHIP, DELETE))).isFalse();
  }

  @Test
  void viewerAndAdminCanReadMembers() {
    assertThat(allows(SystemPolicies.VIEWER, new Action(MEMBERSHIP, READ))).isTrue();
    assertThat(allows(SystemPolicies.ADMIN, new Action(MEMBERSHIP, READ))).isTrue();
  }

  private static boolean allows(SystemPolicyId role, Action action) {
    var document = requireNonNull(new SystemPolicies().find(role));
    var ctx =
        new EvaluationContext(
            new AuthzPrincipal(USER, new Membership.UserMembership(MEMBER, ORG_ID)),
            action,
            new ResourceRef.Existing(action.type(), randomUUID()),
            new ResourceFacts(ORG_ID),
            List.of(document),
            List.of());
    return new DefaultPolicyEngine(THROWING).decide(ctx).allowed();
  }
}
