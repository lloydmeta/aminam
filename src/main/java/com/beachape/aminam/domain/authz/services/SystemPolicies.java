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
import static java.util.stream.Collectors.toUnmodifiableSet;

import com.beachape.aminam.domain.authz.models.Action;
import com.beachape.aminam.domain.authz.models.Effect;
import com.beachape.aminam.domain.authz.models.PolicyDocument;
import com.beachape.aminam.domain.authz.models.PolicyId;
import com.beachape.aminam.domain.authz.models.PolicyId.SystemPolicyId;
import com.beachape.aminam.domain.authz.models.ResourcePattern;
import com.beachape.aminam.domain.authz.models.ResourceType;
import com.beachape.aminam.domain.authz.models.Statement;
import com.beachape.aminam.domain.authz.models.Verb;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/// The seeded system policy catalogue: the RBAC roles, held in code and referenced by reserved id.
/// Attaching a system:* policy to a membership is the "assign a policyId" UX.
@ApplicationScoped
public class SystemPolicies {

  public static final SystemPolicyId VIEWER = new SystemPolicyId(PolicyId.SYSTEM_PREFIX + "viewer");
  public static final SystemPolicyId ADMIN = new SystemPolicyId(PolicyId.SYSTEM_PREFIX + "admin");
  public static final SystemPolicyId MANAGER =
      new SystemPolicyId(PolicyId.SYSTEM_PREFIX + "manager");

  private static final Map<SystemPolicyId, PolicyDocument> CATALOGUE =
      Map.of(VIEWER, viewer(), ADMIN, admin(), MANAGER, manager());

  /// The system policies a manager may assign to a member (the public, world-readable roles). A
  /// system policy has no owning org so it cannot be regime-classified.
  private static final Set<SystemPolicyId> ASSIGNABLE = Set.of(VIEWER, ADMIN, MANAGER);

  public @Nullable PolicyDocument find(SystemPolicyId id) {
    return CATALOGUE.get(id);
  }

  /// Whether a manager may assign this system policy to a member (the standard roles only).
  public boolean isAssignable(SystemPolicyId id) {
    return ASSIGNABLE.contains(id);
  }

  private static PolicyDocument viewer() {
    return new PolicyDocument(
        List.of(
            allow(actions(ORG, READ), ORG),
            allow(actions(DATABASE, READ), DATABASE),
            allow(actions(MEMBERSHIP, READ), MEMBERSHIP),
            allow(actions(SELF_MEMBERSHIP, DELETE), SELF_MEMBERSHIP)));
  }

  private static PolicyDocument admin() {
    return new PolicyDocument(
        List.of(
            allow(actions(ORG, READ), ORG),
            allow(actions(DATABASE, CREATE, READ, UPDATE, DELETE), DATABASE),
            allow(actions(MEMBERSHIP, READ), MEMBERSHIP),
            allow(actions(SELF_MEMBERSHIP, DELETE), SELF_MEMBERSHIP)));
  }

  private static PolicyDocument manager() {
    return new PolicyDocument(
        List.of(
            allow(actions(ORG, READ, UPDATE), ORG),
            allow(actions(DATABASE, CREATE, READ, UPDATE, DELETE), DATABASE),
            allow(actions(DATABASE, ATTACH, DETACH), DATABASE),
            allow(actions(MEMBERSHIP, CREATE, READ, DELETE), MEMBERSHIP),
            allow(actions(MEMBERSHIP, ATTACH, DETACH), MEMBERSHIP),
            allow(actions(SELF_MEMBERSHIP, DELETE), SELF_MEMBERSHIP),
            allow(actions(POLICY, CREATE, READ, UPDATE, DELETE), POLICY)));
  }

  private static Statement allow(Set<Action> actions, ResourceType resourceType) {
    return new Statement(
        Effect.ALLOW, Set.of(), actions, Set.of(ResourcePattern.wildcard(resourceType)), null);
  }

  private static Set<Action> actions(ResourceType type, Verb... verbs) {
    return Arrays.stream(verbs).map(verb -> new Action(type, verb)).collect(toUnmodifiableSet());
  }
}
