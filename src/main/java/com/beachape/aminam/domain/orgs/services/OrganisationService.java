package com.beachape.aminam.domain.orgs.services;

import static com.beachape.aminam.domain.authz.models.ResourceType.MEMBERSHIP;
import static com.beachape.aminam.domain.authz.models.ResourceType.ORG;
import static com.beachape.aminam.domain.authz.models.ResourceType.SELF_MEMBERSHIP;
import static com.beachape.aminam.domain.authz.models.Verb.ATTACH;
import static com.beachape.aminam.domain.authz.models.Verb.CREATE;
import static com.beachape.aminam.domain.authz.models.Verb.DELETE;
import static com.beachape.aminam.domain.authz.models.Verb.DETACH;
import static com.beachape.aminam.domain.authz.models.Verb.READ;
import static java.util.UUID.randomUUID;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static java.util.stream.Collectors.toUnmodifiableList;

import com.beachape.aminam.domain.authc.models.AccessToken;
import com.beachape.aminam.domain.authc.models.AuthenticatedUser;
import com.beachape.aminam.domain.authc.models.User;
import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.authc.repositories.UserRepository;
import com.beachape.aminam.domain.authc.services.TokenService;
import com.beachape.aminam.domain.authz.models.Action;
import com.beachape.aminam.domain.authz.models.AttachmentPoint;
import com.beachape.aminam.domain.authz.models.AttachmentType;
import com.beachape.aminam.domain.authz.models.PolicyAttachment;
import com.beachape.aminam.domain.authz.models.PolicyId;
import com.beachape.aminam.domain.authz.models.ResourceRef;
import com.beachape.aminam.domain.authz.repositories.PolicyAttachmentRepository;
import com.beachape.aminam.domain.authz.services.AuthorisationService;
import com.beachape.aminam.domain.authz.services.AuthorisationService.Check;
import com.beachape.aminam.domain.authz.services.PolicyAuthzService;
import com.beachape.aminam.domain.authz.services.PolicyAuthzService.InvalidPoliciesException;
import com.beachape.aminam.domain.authz.services.SystemPolicies;
import com.beachape.aminam.domain.errors.DomainException;
import com.beachape.aminam.domain.errors.NotAuthorisedException;
import com.beachape.aminam.domain.errors.NotFoundException;
import com.beachape.aminam.domain.orgs.models.Membership;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.beachape.aminam.domain.orgs.models.Organisation;
import com.beachape.aminam.domain.orgs.repositories.MembershipRepository;
import com.beachape.aminam.domain.orgs.repositories.OrganisationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// Organisations and the memberships that anchor a session. Creating an org seats its creator and
/// grants them system:manager; reading one is an ORG:READ decision through the engine. Member
/// management (add/remove/leave) is gated by the engine too.
@ApplicationScoped
public class OrganisationService {

  /// A member of an org: their membership id (the handle another org names to grant cross-org
  /// trust), the user, their username, and the policy ids attached to the membership.
  public record MembershipDetails(
      MembershipId membershipId, UserId userId, String username, List<PolicyId> policyIds) {}

  public static final class MemberAlreadyExistsException extends DomainException {
    public MemberAlreadyExistsException(String username) {
      super("already a member: " + username);
    }

    public MemberAlreadyExistsException(String username, Throwable cause) {
      super("already a member: " + username, cause);
    }
  }

  private final OrganisationRepository organisations;
  private final MembershipRepository memberships;
  private final PolicyAttachmentRepository attachments;
  private final UserRepository users;
  private final AuthorisationService authz;
  private final PolicyAuthzService policyAuthz;
  private final TokenService tokens;
  private final Clock clock;

  @Inject
  OrganisationService(
      OrganisationRepository organisations,
      MembershipRepository memberships,
      PolicyAttachmentRepository attachments,
      UserRepository users,
      AuthorisationService authz,
      PolicyAuthzService policyAuthz,
      TokenService tokens,
      Clock clock) {
    this.organisations = organisations;
    this.memberships = memberships;
    this.attachments = attachments;
    this.users = users;
    this.authz = authz;
    this.policyAuthz = policyAuthz;
    this.tokens = tokens;
    this.clock = clock;
  }

  /// Creates an org, seats the creator, and grants them system:manager.
  @Transactional(rollbackOn = DomainException.class)
  public Organisation create(UserId creator, String name) {
    var org =
        organisations.create(
            new Organisation(new OrgId(randomUUID()), name, creator, clock.instant()));
    var membership = seat(creator, org.id());
    attachments.attach(new PolicyAttachment(pointFor(membership), SystemPolicies.MANAGER));
    return org;
  }

  /// Provisions the personal org every new principal gets at signup.
  @Transactional(rollbackOn = DomainException.class)
  public Organisation provisionPersonalOrg(User user) {
    return create(user.id(), user.username());
  }

  /// The principal's organisations, oldest membership (earliest join) first.
  public List<Organisation> list(UserId principal) {
    return organisations.listByMember(principal);
  }

  /// An org the principal may read in their active session, or throws if it is not visible. A
  /// READ deny and a missing org are both 404, so existence is never leaked.
  public Organisation get(AuthenticatedUser principal, OrgId id) throws NotFoundException {
    requireOrgVisible(principal, id);
    // Re-read guards a concurrent delete between the gather and here.
    var org = organisations.findById(id);
    if (org == null) {
      throw new NotFoundException(NotFoundException.Type.ORGANISATION);
    }
    return org;
  }

  /// Adds an existing user to the org with an initial set of policies.
  @Transactional(rollbackOn = DomainException.class)
  public MembershipDetails addMember(
      AuthenticatedUser actor, OrgId orgId, String username, List<PolicyId> policyIds)
      throws NotFoundException,
          NotAuthorisedException,
          InvalidPoliciesException,
          MemberAlreadyExistsException {
    authz
        .guard(actor)
        .visible(new ResourceRef.Existing(ORG, orgId.value()), NotFoundException.Type.ORGANISATION)
        .permit(new ResourceRef.ToCreate(MEMBERSHIP, orgId), CREATE)
        .check();
    var user = users.findByUsername(username);
    if (user == null) {
      throw new NotFoundException(NotFoundException.Type.USER);
    }
    if (memberships.find(user.id(), orgId) != null) {
      throw new MemberAlreadyExistsException(username);
    }
    Membership membership;
    try {
      membership =
          memberships.create(
              new Membership(new MembershipId(randomUUID()), user.id(), orgId, clock.instant()));
    } catch (MembershipRepository.DuplicateMembershipException e) {
      throw new MemberAlreadyExistsException(username, e);
    }
    policyAuthz.applyPolicies(actor, pointFor(membership), policyIds);
    return memberOf(membership, user);
  }

  /// Replaces a member's policy set with the desired one, then returns the result. READ-first like
  /// removeMember (a caller who cannot read the membership gets 404, no roster leak); a replace can
  /// both add and remove, so it needs both attach and detach (403 otherwise).
  @Transactional(rollbackOn = DomainException.class)
  public MembershipDetails setMemberPolicies(
      AuthenticatedUser actor, OrgId orgId, String username, List<PolicyId> policyIds)
      throws NotFoundException, NotAuthorisedException, InvalidPoliciesException {
    var target = users.findByUsername(username);
    if (target == null) {
      throw new NotFoundException(NotFoundException.Type.MEMBER);
    }
    var membership = memberships.find(target.id(), orgId);
    if (membership == null) {
      throw new NotFoundException(NotFoundException.Type.MEMBER);
    }
    var ref = new ResourceRef.Existing(MEMBERSHIP, membership.id().value());
    authz
        .guard(actor)
        .visible(ref, NotFoundException.Type.MEMBER)
        .permit(ref, ATTACH)
        .permit(ref, DETACH)
        .check();
    policyAuthz.applyPolicies(actor, pointFor(membership), policyIds);
    return memberOf(membership, target);
  }

  /// The org's roster, filtered to the members the actor may read. The org must be visible (404
  /// otherwise); a per-member membership:read check then filters it. The visible members' users and
  /// their policy attachments are each loaded in one batched query.
  public List<MembershipDetails> roster(AuthenticatedUser actor, OrgId orgId)
      throws NotFoundException {
    requireOrgVisible(actor, orgId);
    var rows = memberships.listByOrg(orgId);
    var checks = new ArrayList<Check>(rows.size());
    for (var membership : rows) {
      checks.add(
          new Check(
              new Action(MEMBERSHIP, READ),
              new ResourceRef.Existing(MEMBERSHIP, membership.id().value())));
    }
    var decisions = authz.checkAll(actor, checks);
    var visible = new ArrayList<Membership>();
    for (int i = 0; i < rows.size(); i++) {
      if (decisions.get(i).allowed()) {
        visible.add(rows.get(i));
      }
    }
    if (visible.isEmpty()) {
      return List.of();
    }
    var usersById =
        users.findByIds(visible.stream().map(Membership::userId).collect(toSet())).stream()
            .collect(toMap(User::id, identity()));
    var policiesByPoint =
        attachments
            .findByPoints(visible.stream().map(OrganisationService::pointFor).toList())
            .stream()
            .collect(
                groupingBy(
                    PolicyAttachment::point,
                    mapping(PolicyAttachment::policyId, toUnmodifiableList())));
    var members = new ArrayList<MembershipDetails>();
    for (var membership : visible) {
      var user = usersById.get(membership.userId());
      if (user != null) { // FK guarantees the user exists; defensive against a concurrent delete
        members.add(
            new MembershipDetails(
                membership.id(),
                user.id(),
                user.username(),
                policiesByPoint.getOrDefault(pointFor(membership), List.of())));
      }
    }
    return members;
  }

  /// Removes another member and their attachments.
  /// READ-first: a caller who cannot read the membership gets 404 (no roster leak),
  /// and 403 only when read is allowed but delete is not.
  @Transactional(rollbackOn = DomainException.class)
  public void removeMember(AuthenticatedUser actor, OrgId orgId, String username)
      throws NotAuthorisedException, NotFoundException {
    var target = users.findByUsername(username);
    var membership = target == null ? null : memberships.find(target.id(), orgId);
    if (membership == null) {
      throw new NotFoundException(NotFoundException.Type.MEMBER);
    }
    var ref = new ResourceRef.Existing(MEMBERSHIP, membership.id().value());
    authz.guard(actor).visible(ref, NotFoundException.Type.MEMBER).permit(ref, DELETE).check();
    removeSeat(membership);
  }

  /// The actor leaves the org: deletes their own membership and its identity policies.
  @Transactional(rollbackOn = DomainException.class)
  public void leave(AuthenticatedUser actor, OrgId orgId)
      throws NotFoundException, NotAuthorisedException {
    var membership = memberships.find(actor.id(), orgId);
    if (membership == null) {
      throw new NotFoundException(NotFoundException.Type.MEMBER);
    }
    if (!authz
        .check(
            actor,
            new Action(SELF_MEMBERSHIP, DELETE),
            new ResourceRef.Existing(SELF_MEMBERSHIP, membership.id().value()))
        .allowed()) {
      throw new NotAuthorisedException();
    }
    removeSeat(membership);
  }

  /// The membership a fresh session defaults to: the principal's earliest membership (oldest join),
  /// or null if they have none. Today that is their personal org, joined at signup.
  public @Nullable Membership defaultMembership(UserId principal) {
    var orgs = organisations.listByMember(principal);
    return orgs.isEmpty() ? null : memberships.find(principal, orgs.get(0).id());
  }

  /// Re-mints the session token against the target org after verifying membership.
  public AccessToken switchOrg(AuthenticatedUser current, OrgId target) throws NotFoundException {
    var membership = memberships.find(current.id(), target);
    if (membership == null) {
      throw new NotFoundException(NotFoundException.Type.ORGANISATION);
    }
    return tokens.issue(
        new AuthenticatedUser(current.id(), current.username(), membership.withoutUserId()));
  }

  public void requireOrgVisible(AuthenticatedUser principal, OrgId orgId) throws NotFoundException {
    if (!authz
        .check(principal, new Action(ORG, READ), new ResourceRef.Existing(ORG, orgId.value()))
        .allowed()) {
      throw new NotFoundException(NotFoundException.Type.ORGANISATION);
    }
  }

  private MembershipDetails memberOf(Membership membership, User user) {
    var policyIds =
        attachments.findByPoint(pointFor(membership)).stream()
            .map(PolicyAttachment::policyId)
            .toList();
    return new MembershipDetails(membership.id(), user.id(), user.username(), policyIds);
  }

  private void removeSeat(Membership membership) {
    attachments.deleteByPoint(pointFor(membership));
    memberships.delete(membership);
  }

  private Membership seat(UserId principal, OrgId org) {
    try {
      return memberships.create(
          new Membership(new MembershipId(randomUUID()), principal, org, clock.instant()));
    } catch (MembershipRepository.DuplicateMembershipException e) {
      throw new IllegalStateException("unexpected duplicate membership while seating creator", e);
    }
  }

  private static AttachmentPoint pointFor(Membership membership) {
    return new AttachmentPoint(AttachmentType.MEMBERSHIP, membership.id().value());
  }
}
