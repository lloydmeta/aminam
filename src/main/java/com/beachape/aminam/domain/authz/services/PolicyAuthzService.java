package com.beachape.aminam.domain.authz.services;

import static com.beachape.aminam.domain.authz.models.ResourceType.POLICY;
import static com.beachape.aminam.domain.authz.models.Verb.READ;
import static java.util.stream.Collectors.toSet;

import com.beachape.aminam.domain.authc.models.AuthenticatedUser;
import com.beachape.aminam.domain.authz.models.Action;
import com.beachape.aminam.domain.authz.models.AttachmentPoint;
import com.beachape.aminam.domain.authz.models.PolicyAttachment;
import com.beachape.aminam.domain.authz.models.PolicyId;
import com.beachape.aminam.domain.authz.models.ResourceRef;
import com.beachape.aminam.domain.authz.repositories.PolicyAttachmentRepository;
import com.beachape.aminam.domain.errors.DomainException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/// Handles business logic for policy assignment to an attachment point.
@ApplicationScoped
public class PolicyAuthzService {

  public static final class InvalidPoliciesException extends DomainException {

    public record Failure(PolicyId policyId, String reason) {}

    private final List<Failure> failures;

    public InvalidPoliciesException(List<Failure> failures) {
      super("invalid policies: " + failures);
      this.failures = List.copyOf(failures);
    }

    public List<Failure> failures() {
      return failures;
    }
  }

  private final SystemPolicies systemPolicies;
  private final AuthorisationService authz;
  private final PolicyAttachmentRepository attachments;

  @Inject
  PolicyAuthzService(
      SystemPolicies systemPolicies,
      AuthorisationService authz,
      PolicyAttachmentRepository attachments) {
    this.systemPolicies = systemPolicies;
    this.authz = authz;
    this.attachments = attachments;
  }

  public boolean assignable(AuthenticatedUser actor, PolicyId id) {
    return switch (id) {
      case PolicyId.SystemPolicyId system -> systemPolicies.isAssignable(system);
      case PolicyId.CustomPolicyId custom ->
          authz
              .check(
                  actor, new Action(POLICY, READ), new ResourceRef.Existing(POLICY, custom.value()))
              .allowed();
    };
  }

  /// *Replaces* the policies on a point with the desired set.
  public void applyPolicies(AuthenticatedUser actor, AttachmentPoint point, List<PolicyId> desired)
      throws InvalidPoliciesException {
    var failures = new ArrayList<InvalidPoliciesException.Failure>();
    for (var id : desired) {
      if (!assignable(actor, id)) {
        failures.add(new InvalidPoliciesException.Failure(id, "unknown or not assignable policy"));
      }
    }
    if (!failures.isEmpty()) {
      throw new InvalidPoliciesException(failures);
    }
    var current =
        attachments.findByPoint(point).stream().map(PolicyAttachment::policyId).collect(toSet());
    var want = Set.copyOf(desired);
    for (var id : want) {
      if (!current.contains(id)) {
        attachments.attach(new PolicyAttachment(point, id));
      }
    }
    for (var id : current) {
      if (!want.contains(id)) {
        attachments.detach(new PolicyAttachment(point, id));
      }
    }
  }
}
