package com.beachape.aminam.domain.orgs.services;

import static com.beachape.aminam.domain.authz.models.ResourceType.MEMBERSHIP;
import static com.beachape.aminam.domain.authz.models.ResourceType.ORG;
import static com.beachape.aminam.domain.authz.models.ResourceType.SELF_MEMBERSHIP;
import static com.beachape.aminam.domain.authz.models.Verb.ATTACH;
import static com.beachape.aminam.domain.authz.models.Verb.CREATE;
import static com.beachape.aminam.domain.authz.models.Verb.DELETE;
import static com.beachape.aminam.domain.authz.models.Verb.DETACH;
import static com.beachape.aminam.domain.authz.services.SystemPolicies.ADMIN;
import static com.beachape.aminam.domain.authz.services.SystemPolicies.MANAGER;
import static com.beachape.aminam.domain.authz.services.SystemPolicies.VIEWER;
import static java.time.ZoneOffset.UTC;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.beachape.aminam.domain.authc.models.AccessToken;
import com.beachape.aminam.domain.authc.models.AuthenticatedUser;
import com.beachape.aminam.domain.authc.models.PasswordHash;
import com.beachape.aminam.domain.authc.models.User;
import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.authc.repositories.UserRepository;
import com.beachape.aminam.domain.authc.services.TokenService;
import com.beachape.aminam.domain.authz.models.AttachmentPoint;
import com.beachape.aminam.domain.authz.models.AttachmentType;
import com.beachape.aminam.domain.authz.models.PolicyAttachment;
import com.beachape.aminam.domain.authz.models.PolicyId.SystemPolicyId;
import com.beachape.aminam.domain.authz.models.ResourceRef;
import com.beachape.aminam.domain.authz.repositories.PolicyAttachmentRepository;
import com.beachape.aminam.domain.authz.services.GuardStub;
import com.beachape.aminam.domain.authz.services.PolicyAuthzService;
import com.beachape.aminam.domain.authz.services.PolicyAuthzService.InvalidPoliciesException;
import com.beachape.aminam.domain.authz.services.PolicyAuthzService.InvalidPoliciesException.Failure;
import com.beachape.aminam.domain.authz.services.SystemPolicies;
import com.beachape.aminam.domain.errors.NotAuthorisedException;
import com.beachape.aminam.domain.errors.NotFoundException;
import com.beachape.aminam.domain.orgs.models.Membership;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.beachape.aminam.domain.orgs.models.Organisation;
import com.beachape.aminam.domain.orgs.repositories.MembershipRepository;
import com.beachape.aminam.domain.orgs.repositories.OrganisationRepository;
import com.beachape.aminam.domain.orgs.services.OrganisationService.MemberAlreadyExistsException;
import com.beachape.aminam.domain.orgs.services.OrganisationService.MembershipDetails;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class OrganisationServiceTest {

  private static final Clock FIXED = Clock.fixed(Instant.parse("2026-06-20T00:00:00Z"), UTC);
  private static final UserId CREATOR = new UserId(randomUUID());
  private static final MembershipId ACTIVE_MEMBERSHIP = new MembershipId(randomUUID());
  private static final OrgId ORG_ID = new OrgId(randomUUID());
  private static final ResourceRef ORG_REF = new ResourceRef.Existing(ORG, ORG_ID.value());
  private static final ResourceRef TO_CREATE = new ResourceRef.ToCreate(MEMBERSHIP, ORG_ID);

  private final OrganisationRepository organisations = mock();
  private final MembershipRepository memberships = mock();
  private final PolicyAttachmentRepository attachments = mock();
  private final UserRepository users = mock();
  private final PolicyAuthzService policyAuthz = mock();
  private final TokenService tokens = mock();
  private final GuardStub guard = new GuardStub();
  private final OrganisationService service =
      new OrganisationService(
          organisations,
          memberships,
          attachments,
          users,
          guard.authz(),
          policyAuthz,
          tokens,
          FIXED);

  @Test
  void createSeatsTheCreatorAndGrantsManager() throws Exception {
    when(organisations.create(any())).thenAnswer(inv -> inv.getArgument(0));
    when(memberships.create(any())).thenAnswer(inv -> inv.getArgument(0));

    var org = service.create(CREATOR, "acme");

    assertThat(org.name()).isEqualTo("acme");
    assertThat(org.createdBy()).isEqualTo(CREATOR);
    assertThat(org.createdAt()).isEqualTo(FIXED.instant());

    var seated = ArgumentCaptor.forClass(Membership.class);
    verify(memberships).create(seated.capture());
    assertThat(seated.getValue().userId()).isEqualTo(CREATOR);
    assertThat(seated.getValue().orgId()).isEqualTo(org.id());

    var attached = ArgumentCaptor.forClass(PolicyAttachment.class);
    verify(attachments).attach(attached.capture());
    assertThat(attached.getValue().policyId()).isEqualTo(SystemPolicies.MANAGER);
    assertThat(attached.getValue().point().type()).isEqualTo(AttachmentType.MEMBERSHIP);
    assertThat(attached.getValue().point().id()).isEqualTo(seated.getValue().id().value());
  }

  @Test
  void provisionPersonalOrgNamesItAfterTheUser() throws Exception {
    when(organisations.create(any())).thenAnswer(inv -> inv.getArgument(0));
    when(memberships.create(any())).thenAnswer(inv -> inv.getArgument(0));
    var user = new User(CREATOR, "lloyd", new PasswordHash("$2a$10$hash"), FIXED.instant());

    var org = service.provisionPersonalOrg(user);

    assertThat(org.name()).isEqualTo("lloyd");
    assertThat(org.createdBy()).isEqualTo(CREATOR);
  }

  @Test
  void getReturnsTheOrgWhenReadIsAllowed() throws Exception {
    var org = new Organisation(ORG_ID, "acme", CREATOR, FIXED.instant());
    guard.visible(ORG_REF);
    when(organisations.findById(ORG_ID)).thenReturn(org);

    assertThat(service.get(actor(), ORG_ID)).isEqualTo(org);
  }

  @Test
  void getThrowsWhenReadIsDenied() {
    guard.invisible(ORG_REF);

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.get(actor(), ORG_ID))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.ORGANISATION));
  }

  @Test
  void getThrowsWhenAllowedButTheOrgIsAbsent() {
    guard.visible(ORG_REF);
    when(organisations.findById(ORG_ID)).thenReturn(null);

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.get(actor(), ORG_ID))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.ORGANISATION));
  }

  @Test
  void addMemberSeatsTheUserAndAppliesTheRolesToItsMembership() throws Exception {
    guard.visible(ORG_REF);
    guard.permit(TO_CREATE, CREATE);
    var lloyd = user("lloyd");
    when(users.findByUsername("lloyd")).thenReturn(lloyd);
    when(memberships.find(lloyd.id(), ORG_ID)).thenReturn(null);
    when(memberships.create(any())).thenAnswer(inv -> inv.getArgument(0));

    service.addMember(actor(), ORG_ID, "lloyd", List.of(VIEWER, ADMIN));

    var seated = ArgumentCaptor.forClass(Membership.class);
    verify(memberships).create(seated.capture());
    assertThat(seated.getValue().userId()).isEqualTo(lloyd.id());
    assertThat(seated.getValue().orgId()).isEqualTo(ORG_ID);

    var point = ArgumentCaptor.forClass(AttachmentPoint.class);
    verify(policyAuthz).applyPolicies(any(), point.capture(), eq(List.of(VIEWER, ADMIN)));
    assertThat(point.getValue().type()).isEqualTo(AttachmentType.MEMBERSHIP);
    assertThat(point.getValue().id()).isEqualTo(seated.getValue().id().value());
  }

  @Test
  void addMemberDeniedWhenCreateNotPermitted() throws Exception {
    guard.visible(ORG_REF);
    guard.forbid(TO_CREATE, CREATE);

    assertThatExceptionOfType(NotAuthorisedException.class)
        .isThrownBy(() -> service.addMember(actor(), ORG_ID, "lloyd", List.of(VIEWER)));
    verify(memberships, never()).create(any());
  }

  @Test
  void addMember404WhenOrgNotVisible() {
    guard.invisible(ORG_REF);

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.addMember(actor(), ORG_ID, "lloyd", List.of(VIEWER)))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.ORGANISATION));
  }

  @Test
  void addMemberPropagatesInvalidPolicies() throws Exception {
    // Which ids are assignable is PolicyAuthzService.applyPolicies' concern (tested there); here we
    // only confirm addMember lets the failure propagate (the transaction rolls back).
    guard.visible(ORG_REF);
    guard.permit(TO_CREATE, CREATE);
    var lloyd = user("lloyd");
    when(users.findByUsername("lloyd")).thenReturn(lloyd);
    when(memberships.find(lloyd.id(), ORG_ID)).thenReturn(null);
    when(memberships.create(any())).thenAnswer(inv -> inv.getArgument(0));
    var bad = new SystemPolicyId("system:nope");
    doThrow(
            new InvalidPoliciesException(
                List.of(new Failure(bad, "unknown or not assignable policy"))))
        .when(policyAuthz)
        .applyPolicies(any(), any(), any());

    assertThatExceptionOfType(InvalidPoliciesException.class)
        .isThrownBy(() -> service.addMember(actor(), ORG_ID, "lloyd", List.of(bad)))
        .satisfies(
            ex ->
                assertThat(ex.failures())
                    .extracting(f -> f.policyId().asText())
                    .containsExactly("system:nope"));
  }

  @Test
  void addMemberRejectsAnUnknownUser() {
    guard.visible(ORG_REF);
    guard.permit(TO_CREATE, CREATE);
    when(users.findByUsername("ghost")).thenReturn(null);

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.addMember(actor(), ORG_ID, "ghost", List.of(VIEWER)))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.USER));
  }

  @Test
  void addMemberRejectsAnExistingMember() throws Exception {
    guard.visible(ORG_REF);
    guard.permit(TO_CREATE, CREATE);
    var lloyd = user("lloyd");
    when(users.findByUsername("lloyd")).thenReturn(lloyd);
    when(memberships.find(lloyd.id(), ORG_ID)).thenReturn(membership(lloyd.id(), ORG_ID));

    assertThatExceptionOfType(MemberAlreadyExistsException.class)
        .isThrownBy(() -> service.addMember(actor(), ORG_ID, "lloyd", List.of(VIEWER)));
    verify(memberships, never()).create(any());
  }

  @Test
  void removeMemberDeletesTheMembershipAndItsAttachments() throws Exception {
    var lloyd = user("lloyd");
    var membership = membership(lloyd.id(), ORG_ID);
    when(users.findByUsername("lloyd")).thenReturn(lloyd);
    when(memberships.find(lloyd.id(), ORG_ID)).thenReturn(membership);
    guard.visible(refOf(membership));
    guard.permit(refOf(membership), DELETE);

    service.removeMember(actor(), ORG_ID, "lloyd");

    var point = ArgumentCaptor.forClass(AttachmentPoint.class);
    verify(attachments).deleteByPoint(point.capture());
    assertThat(point.getValue().id()).isEqualTo(membership.id().value());
    verify(memberships).delete(membership);
  }

  @Test
  void removeMemberDeniedWhenDeleteNotPermitted() {
    var lloyd = user("lloyd");
    var membership = membership(lloyd.id(), ORG_ID);
    when(users.findByUsername("lloyd")).thenReturn(lloyd);
    when(memberships.find(lloyd.id(), ORG_ID)).thenReturn(membership);
    guard.visible(refOf(membership));
    guard.forbid(refOf(membership), DELETE);

    assertThatExceptionOfType(NotAuthorisedException.class)
        .isThrownBy(() -> service.removeMember(actor(), ORG_ID, "lloyd"));
    verify(memberships, never()).delete(any());
  }

  @Test
  void removeMember404WhenTargetIsNotReadable() {
    var lloyd = user("lloyd");
    var membership = membership(lloyd.id(), ORG_ID);
    when(users.findByUsername("lloyd")).thenReturn(lloyd);
    when(memberships.find(lloyd.id(), ORG_ID)).thenReturn(membership);
    guard.invisible(refOf(membership));

    // Same 404 as a non-member, so the roster's existence is not leaked.
    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.removeMember(actor(), ORG_ID, "lloyd"))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.MEMBER));
    verify(memberships, never()).delete(any());
  }

  @Test
  void removeMember404WhenTargetIsNotAMember() {
    var lloyd = user("lloyd");
    when(users.findByUsername("lloyd")).thenReturn(lloyd);
    when(memberships.find(lloyd.id(), ORG_ID)).thenReturn(null);

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.removeMember(actor(), ORG_ID, "lloyd"))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.MEMBER));
  }

  @Test
  void leaveDeletesTheActorsOwnMembershipAndAttachments() throws Exception {
    var membership = membership(CREATOR, ORG_ID);
    when(memberships.find(CREATOR, ORG_ID)).thenReturn(membership);
    guard.permit(selfRef(membership), DELETE);

    service.leave(actor(), ORG_ID);

    verify(attachments).deleteByPoint(any());
    verify(memberships).delete(membership);
  }

  @Test
  void leave404WhenNotAMember() {
    when(memberships.find(CREATOR, ORG_ID)).thenReturn(null);

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.leave(actor(), ORG_ID))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.MEMBER));
  }

  @Test
  void leaveDeniedWhenSelfDeleteNotPermitted() {
    var membership = membership(CREATOR, ORG_ID);
    when(memberships.find(CREATOR, ORG_ID)).thenReturn(membership);
    guard.forbid(selfRef(membership), DELETE);

    assertThatExceptionOfType(NotAuthorisedException.class)
        .isThrownBy(() -> service.leave(actor(), ORG_ID));
    verify(memberships, never()).delete(any());
  }

  @Test
  void setMemberPoliciesAppliesTheDesiredSetToTheMembership() throws Exception {
    var lloyd = user("lloyd");
    var m = membership(lloyd.id(), ORG_ID);
    when(users.findByUsername("lloyd")).thenReturn(lloyd);
    when(memberships.find(lloyd.id(), ORG_ID)).thenReturn(m);
    guard.visible(refOf(m));
    guard.permit(refOf(m), ATTACH);
    guard.permit(refOf(m), DETACH);

    service.setMemberPolicies(actor(), ORG_ID, "lloyd", List.of(ADMIN));

    verify(policyAuthz).applyPolicies(any(), eq(point(m)), eq(List.of(ADMIN)));
  }

  @Test
  void setMemberPoliciesPropagatesInvalidPolicies() throws Exception {
    var lloyd = user("lloyd");
    var m = membership(lloyd.id(), ORG_ID);
    when(users.findByUsername("lloyd")).thenReturn(lloyd);
    when(memberships.find(lloyd.id(), ORG_ID)).thenReturn(m);
    guard.visible(refOf(m));
    guard.permit(refOf(m), ATTACH);
    guard.permit(refOf(m), DETACH);
    var bad = new SystemPolicyId("system:nope");
    doThrow(
            new InvalidPoliciesException(
                List.of(new Failure(bad, "unknown or not assignable policy"))))
        .when(policyAuthz)
        .applyPolicies(any(), any(), any());

    assertThatExceptionOfType(InvalidPoliciesException.class)
        .isThrownBy(() -> service.setMemberPolicies(actor(), ORG_ID, "lloyd", List.of(bad)));
  }

  @Test
  void setMemberPolicies403WhenAttachDenied() throws Exception {
    var lloyd = user("lloyd");
    var m = membership(lloyd.id(), ORG_ID);
    when(users.findByUsername("lloyd")).thenReturn(lloyd);
    when(memberships.find(lloyd.id(), ORG_ID)).thenReturn(m);
    guard.visible(refOf(m));
    guard.forbid(refOf(m), ATTACH);
    guard.permit(refOf(m), DETACH);

    assertThatExceptionOfType(NotAuthorisedException.class)
        .isThrownBy(() -> service.setMemberPolicies(actor(), ORG_ID, "lloyd", List.of(ADMIN)));
    verify(policyAuthz, never()).applyPolicies(any(), any(), any());
  }

  @Test
  void setMemberPolicies403WhenDetachDenied() throws Exception {
    var lloyd = user("lloyd");
    var m = membership(lloyd.id(), ORG_ID);
    when(users.findByUsername("lloyd")).thenReturn(lloyd);
    when(memberships.find(lloyd.id(), ORG_ID)).thenReturn(m);
    guard.visible(refOf(m));
    guard.permit(refOf(m), ATTACH);
    guard.forbid(refOf(m), DETACH);

    assertThatExceptionOfType(NotAuthorisedException.class)
        .isThrownBy(() -> service.setMemberPolicies(actor(), ORG_ID, "lloyd", List.of(ADMIN)));
    verify(policyAuthz, never()).applyPolicies(any(), any(), any());
  }

  @Test
  void setMemberPolicies404WhenTargetIsNotReadable() throws Exception {
    var lloyd = user("lloyd");
    var m = membership(lloyd.id(), ORG_ID);
    when(users.findByUsername("lloyd")).thenReturn(lloyd);
    when(memberships.find(lloyd.id(), ORG_ID)).thenReturn(m);
    guard.invisible(refOf(m));

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.setMemberPolicies(actor(), ORG_ID, "lloyd", List.of(ADMIN)))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.MEMBER));
    verify(policyAuthz, never()).applyPolicies(any(), any(), any());
  }

  @Test
  void setMemberPolicies404WhenUserUnknown() throws Exception {
    when(users.findByUsername("ghost")).thenReturn(null);

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.setMemberPolicies(actor(), ORG_ID, "ghost", List.of(ADMIN)))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.MEMBER));
    verify(policyAuthz, never()).applyPolicies(any(), any(), any());
  }

  @Test
  void rosterReturnsReadableMembersEachWithTheirOwnPolicies() throws Exception {
    guard.visible(ORG_REF);
    var lloyd = user("lloyd");
    var joe = user("joe");
    var m1 = membership(lloyd.id(), ORG_ID);
    var m2 = membership(joe.id(), ORG_ID);
    guard.visible(refOf(m1));
    guard.visible(refOf(m2));
    when(memberships.listByOrg(ORG_ID)).thenReturn(List.of(m1, m2));
    when(users.findByIds(any())).thenReturn(List.of(lloyd, joe));
    when(attachments.findByPoints(any()))
        .thenReturn(
            List.of(
                new PolicyAttachment(point(m1), VIEWER),
                new PolicyAttachment(point(m2), ADMIN),
                new PolicyAttachment(point(m2), MANAGER)));

    var roster = service.roster(actor(), ORG_ID);

    assertThat(roster)
        .extracting(MembershipDetails::username)
        .containsExactlyInAnyOrder("lloyd", "joe");
    assertThat(memberNamed(roster, "lloyd").policyIds()).containsExactly(VIEWER);
    assertThat(memberNamed(roster, "joe").policyIds()).containsExactlyInAnyOrder(ADMIN, MANAGER);
  }

  @Test
  void rosterExcludesMembersTheActorCannotRead() throws Exception {
    guard.visible(ORG_REF);
    var lloyd = user("lloyd");
    var joe = user("joe");
    var m1 = membership(lloyd.id(), ORG_ID);
    var m2 = membership(joe.id(), ORG_ID);
    guard.visible(refOf(m1));
    guard.invisible(refOf(m2));
    when(memberships.listByOrg(ORG_ID)).thenReturn(List.of(m1, m2));
    when(users.findByIds(any())).thenReturn(List.of(lloyd));

    var roster = service.roster(actor(), ORG_ID);

    assertThat(roster).extracting(MembershipDetails::username).containsExactly("lloyd");
  }

  @Test
  void rosterSkipsAVisibleMemberWhoseUserRowVanished() throws Exception {
    guard.visible(ORG_REF);
    var lloyd = user("lloyd");
    var joe = user("joe");
    var m1 = membership(lloyd.id(), ORG_ID);
    var m2 = membership(joe.id(), ORG_ID);
    guard.visible(refOf(m1));
    guard.visible(refOf(m2));
    when(memberships.listByOrg(ORG_ID)).thenReturn(List.of(m1, m2));
    // Both are READ-allowed, but joe's user row is gone (concurrent delete): the batch omits her.
    when(users.findByIds(any())).thenReturn(List.of(lloyd));
    when(attachments.findByPoints(any()))
        .thenReturn(List.of(new PolicyAttachment(point(m1), VIEWER)));

    var roster = service.roster(actor(), ORG_ID);

    assertThat(roster).extracting(MembershipDetails::username).containsExactly("lloyd");
  }

  @Test
  void roster404WhenOrgNotVisible() {
    guard.invisible(ORG_REF);

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.roster(actor(), ORG_ID))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.ORGANISATION));
  }

  @Test
  void defaultMembershipIsTheOneInTheOldestOrg() {
    var oldest = new Organisation(new OrgId(randomUUID()), "first", CREATOR, FIXED.instant());
    var newer =
        new Organisation(
            new OrgId(randomUUID()), "second", CREATOR, FIXED.instant().plusSeconds(1));
    when(organisations.listByMember(CREATOR)).thenReturn(List.of(oldest, newer));
    var membership = membership(CREATOR, oldest.id());
    when(memberships.find(CREATOR, oldest.id())).thenReturn(membership);

    assertThat(service.defaultMembership(CREATOR)).isEqualTo(membership);
  }

  @Test
  void defaultMembershipIsNullWhenTheUserHasNoOrgs() {
    when(organisations.listByMember(CREATOR)).thenReturn(List.of());

    assertThat(service.defaultMembership(CREATOR)).isNull();
  }

  @Test
  void switchOrgReMintsAgainstAVerifiedMembership() throws Exception {
    var target = new OrgId(randomUUID());
    var membership = membership(CREATOR, target);
    when(memberships.find(CREATOR, target)).thenReturn(membership);
    when(tokens.issue(any())).thenReturn(new AccessToken("minted"));

    var token = service.switchOrg(new AuthenticatedUser(CREATOR, "lloyd"), target);

    assertThat(token).isEqualTo(new AccessToken("minted"));
    var issued = ArgumentCaptor.forClass(AuthenticatedUser.class);
    verify(tokens).issue(issued.capture());
    assertThat(issued.getValue().activeOrg()).isEqualTo(target);
    assertThat(issued.getValue().activeMembership()).isEqualTo(membership.withoutUserId());
  }

  @Test
  void switchOrgThrowsWhenTheUserIsNotAMember() {
    var target = new OrgId(randomUUID());
    when(memberships.find(CREATOR, target)).thenReturn(null);

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.switchOrg(new AuthenticatedUser(CREATOR, "lloyd"), target))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.ORGANISATION));
    verify(tokens, never()).issue(any());
  }

  private static AuthenticatedUser actor() {
    return new AuthenticatedUser(
        CREATOR, "lloyd", new Membership.UserMembership(ACTIVE_MEMBERSHIP, ORG_ID));
  }

  private static User user(String name) {
    return new User(
        new UserId(randomUUID()), name, new PasswordHash("$2a$10$hash"), FIXED.instant());
  }

  private static Membership membership(UserId principal, OrgId org) {
    return new Membership(new MembershipId(randomUUID()), principal, org, FIXED.instant());
  }

  private static AttachmentPoint point(Membership membership) {
    return new AttachmentPoint(AttachmentType.MEMBERSHIP, membership.id().value());
  }

  private static ResourceRef refOf(Membership membership) {
    return new ResourceRef.Existing(MEMBERSHIP, membership.id().value());
  }

  private static ResourceRef selfRef(Membership membership) {
    return new ResourceRef.Existing(SELF_MEMBERSHIP, membership.id().value());
  }

  private static MembershipDetails memberNamed(List<MembershipDetails> roster, String username) {
    return roster.stream().filter(m -> m.username().equals(username)).findFirst().orElseThrow();
  }
}
