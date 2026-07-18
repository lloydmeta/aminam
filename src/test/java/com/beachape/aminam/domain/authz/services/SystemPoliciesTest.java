package com.beachape.aminam.domain.authz.services;

import static com.beachape.aminam.domain.authz.models.ResourceType.DATABASE;
import static com.beachape.aminam.domain.authz.models.ResourceType.MEMBERSHIP;
import static com.beachape.aminam.domain.authz.models.ResourceType.ORG;
import static com.beachape.aminam.domain.authz.models.ResourceType.POLICY;
import static com.beachape.aminam.domain.authz.models.ResourceType.SELF_MEMBERSHIP;
import static com.beachape.aminam.domain.authz.models.Verb.ATTACH;
import static com.beachape.aminam.domain.authz.models.Verb.CREATE;
import static com.beachape.aminam.domain.authz.models.Verb.DELETE;
import static com.beachape.aminam.domain.authz.models.Verb.DETACH;
import static com.beachape.aminam.domain.authz.models.Verb.READ;
import static com.beachape.aminam.domain.authz.models.Verb.UPDATE;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.domain.authz.models.Action;
import com.beachape.aminam.domain.authz.models.PolicyId.SystemPolicyId;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SystemPoliciesTest {

  private final SystemPolicies policies = new SystemPolicies();

  @Test
  void viewerReadsResourcesAndCanSelfLeave() {
    assertThat(actionsOf(SystemPolicies.VIEWER))
        .containsExactlyInAnyOrder(
            new Action(ORG, READ),
            new Action(DATABASE, READ),
            new Action(MEMBERSHIP, READ),
            new Action(SELF_MEMBERSHIP, DELETE));
  }

  @Test
  void viewerAndAdminReadMembersButCannotManageThem() {
    for (var id : Set.of(SystemPolicies.VIEWER, SystemPolicies.ADMIN)) {
      var actions = actionsOf(id);
      assertThat(actions).contains(new Action(MEMBERSHIP, READ));
      assertThat(actions)
          .doesNotContain(
              new Action(MEMBERSHIP, CREATE),
              new Action(MEMBERSHIP, DELETE),
              new Action(MEMBERSHIP, ATTACH),
              new Action(MEMBERSHIP, DETACH));
    }
  }

  @Test
  void everyRoleCanSelfLeave() {
    for (var id : Set.of(SystemPolicies.VIEWER, SystemPolicies.ADMIN, SystemPolicies.MANAGER)) {
      assertThat(actionsOf(id)).contains(new Action(SELF_MEMBERSHIP, DELETE));
    }
  }

  @Test
  void adminHasDatabaseCrudButNoGrantVerbs() {
    var actions = actionsOf(SystemPolicies.ADMIN);
    assertThat(actions)
        .contains(
            new Action(DATABASE, CREATE),
            new Action(DATABASE, READ),
            new Action(DATABASE, UPDATE),
            new Action(DATABASE, DELETE),
            new Action(ORG, READ));
    assertThat(actions)
        .doesNotContain(
            new Action(DATABASE, ATTACH),
            new Action(DATABASE, DETACH),
            new Action(MEMBERSHIP, ATTACH),
            new Action(POLICY, READ));
  }

  @Test
  void managerHoldsTheGrantVerbs() {
    var actions = actionsOf(SystemPolicies.MANAGER);
    assertThat(actions)
        .contains(
            new Action(ORG, READ),
            new Action(ORG, UPDATE),
            new Action(DATABASE, ATTACH),
            new Action(DATABASE, DETACH),
            new Action(MEMBERSHIP, CREATE),
            new Action(MEMBERSHIP, READ),
            new Action(MEMBERSHIP, DELETE),
            new Action(MEMBERSHIP, ATTACH),
            new Action(MEMBERSHIP, DETACH),
            new Action(POLICY, CREATE),
            new Action(POLICY, READ),
            new Action(POLICY, UPDATE),
            new Action(POLICY, DELETE));
  }

  @Test
  void membershipHasNoUpdateVerb() {
    assertThat(actionsOf(SystemPolicies.MANAGER)).doesNotContain(new Action(MEMBERSHIP, UPDATE));
  }

  @Test
  void everyResourcePatternIsAWildcard() {
    for (var id : Set.of(SystemPolicies.VIEWER, SystemPolicies.ADMIN, SystemPolicies.MANAGER)) {
      requireNonNull(policies.find(id)).statements().stream()
          .flatMap(statement -> statement.resources().stream())
          .forEach(pattern -> assertThat(pattern.id()).isNull());
    }
  }

  @Test
  void unknownSystemPolicyIdResolvesToNull() {
    assertThat(policies.find(new SystemPolicyId("system:nope"))).isNull();
  }

  private Set<Action> actionsOf(SystemPolicyId id) {
    return requireNonNull(policies.find(id)).statements().stream()
        .flatMap(statement -> statement.actions().stream())
        .collect(toUnmodifiableSet());
  }
}
