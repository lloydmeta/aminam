package com.beachape.aminam.domain.authz.services;

import static com.beachape.aminam.domain.authz.models.ResourceType.ORG;
import static com.beachape.aminam.domain.authz.models.ResourceType.POLICY;
import static com.beachape.aminam.domain.authz.models.Verb.CREATE;
import static com.beachape.aminam.domain.authz.models.Verb.DELETE;
import static com.beachape.aminam.domain.authz.models.Verb.READ;
import static com.beachape.aminam.domain.authz.models.Verb.UPDATE;
import static java.util.UUID.randomUUID;

import com.beachape.aminam.domain.authc.models.AuthenticatedUser;
import com.beachape.aminam.domain.authz.models.Action;
import com.beachape.aminam.domain.authz.models.Policy;
import com.beachape.aminam.domain.authz.models.PolicyDocument;
import com.beachape.aminam.domain.authz.models.PolicyId.CustomPolicyId;
import com.beachape.aminam.domain.authz.models.ResourceRef;
import com.beachape.aminam.domain.authz.repositories.PolicyAttachmentRepository;
import com.beachape.aminam.domain.authz.repositories.PolicyRepository;
import com.beachape.aminam.domain.authz.services.AuthorisationService.Check;
import com.beachape.aminam.domain.authz.services.PolicyValidator.InvalidPolicyException;
import com.beachape.aminam.domain.errors.DomainException;
import com.beachape.aminam.domain.errors.NotAuthorisedException;
import com.beachape.aminam.domain.errors.NotFoundException;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.beachape.aminam.domain.orgs.services.OrganisationService;
import com.beachape.aminam.domain.repositories.errors.EntityNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/// Authoring of custom policies, gated by the engine: the org must be visible (404 otherwise) and
/// policy:create/read/update/delete confine authoring to the actor's own org (a cross-org actor is
/// denied by the classifier). Documents are CEL-validated before they are stored.
@ApplicationScoped
public class PolicyService {

  private final PolicyRepository policies;
  private final PolicyAttachmentRepository attachments;
  private final PolicyValidator validator;
  private final AuthorisationService authz;
  private final OrganisationService organisations;
  private final Clock clock;

  @Inject
  PolicyService(
      PolicyRepository policies,
      PolicyAttachmentRepository attachments,
      PolicyValidator validator,
      AuthorisationService authz,
      OrganisationService organisations,
      Clock clock) {
    this.policies = policies;
    this.attachments = attachments;
    this.validator = validator;
    this.authz = authz;
    this.organisations = organisations;
    this.clock = clock;
  }

  @Transactional(rollbackOn = DomainException.class)
  public Policy create(AuthenticatedUser actor, OrgId orgId, String name, PolicyDocument document)
      throws NotFoundException, NotAuthorisedException, InvalidPolicyException {
    authz
        .guard(actor)
        .visible(new ResourceRef.Existing(ORG, orgId.value()), NotFoundException.Type.ORGANISATION)
        .permit(new ResourceRef.ToCreate(POLICY, orgId), CREATE)
        .check();
    validator.validate(document);
    return policies.create(
        new Policy(new CustomPolicyId(randomUUID()), orgId, name, document, clock.instant()));
  }

  public List<Policy> list(AuthenticatedUser actor, OrgId orgId) throws NotFoundException {
    organisations.requireOrgVisible(actor, orgId);
    var rows = policies.listByOrg(orgId);
    var checks =
        rows.stream()
            .map(policy -> new Check(new Action(POLICY, READ), refFor(policy.id())))
            .toList();
    var decisions = authz.checkAll(actor, checks);
    var visible = new ArrayList<Policy>();
    for (int i = 0; i < rows.size(); i++) {
      if (decisions.get(i).allowed()) {
        visible.add(rows.get(i));
      }
    }
    return visible;
  }

  public Policy get(AuthenticatedUser actor, OrgId orgId, CustomPolicyId id)
      throws NotFoundException {
    organisations.requireOrgVisible(actor, orgId);
    return requireRead(actor, id);
  }

  @Transactional(rollbackOn = DomainException.class)
  public Policy update(
      AuthenticatedUser actor, OrgId orgId, CustomPolicyId id, String name, PolicyDocument document)
      throws NotFoundException, NotAuthorisedException, InvalidPolicyException {
    var existing =
        authz
            .guard(actor)
            .visible(
                new ResourceRef.Existing(ORG, orgId.value()), NotFoundException.Type.ORGANISATION)
            .visible(refFor(id), NotFoundException.Type.POLICY)
            .permit(refFor(id), UPDATE)
            .fetch(NotFoundException.Type.POLICY, () -> policies.findById(id));
    validator.validate(document);
    try {
      return policies.update(
          new Policy(existing.id(), existing.orgId(), name, document, existing.createdAt()));
    } catch (EntityNotFoundException e) {
      // The row vanished between the read check and the write (concurrent delete) -> 404.
      throw new NotFoundException(NotFoundException.Type.POLICY, e);
    }
  }

  @Transactional(rollbackOn = DomainException.class)
  public void delete(AuthenticatedUser actor, OrgId orgId, CustomPolicyId id)
      throws NotFoundException, NotAuthorisedException {
    var policy =
        authz
            .guard(actor)
            .visible(
                new ResourceRef.Existing(ORG, orgId.value()), NotFoundException.Type.ORGANISATION)
            .visible(refFor(id), NotFoundException.Type.POLICY)
            .permit(refFor(id), DELETE)
            .fetch(NotFoundException.Type.POLICY, () -> policies.findById(id));
    // Cascade the grants: drop every attachment of this policy in the same transaction so no orphan
    // attachment row survives.
    attachments.deleteByPolicyId(id);
    policies.delete(policy);
  }

  private Policy requireRead(AuthenticatedUser actor, CustomPolicyId id) throws NotFoundException {
    if (!authz.check(actor, new Action(POLICY, READ), refFor(id)).allowed()) {
      throw new NotFoundException(NotFoundException.Type.POLICY);
    }
    var policy = policies.findById(id);
    if (policy == null) {
      throw new NotFoundException(NotFoundException.Type.POLICY);
    }
    return policy;
  }

  private static ResourceRef.Existing refFor(CustomPolicyId id) {
    return new ResourceRef.Existing(POLICY, id.value());
  }
}
