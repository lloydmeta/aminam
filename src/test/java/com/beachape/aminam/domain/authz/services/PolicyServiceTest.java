package com.beachape.aminam.domain.authz.services;

import static com.beachape.aminam.domain.authz.models.ResourceType.POLICY;
import static com.beachape.aminam.domain.authz.models.Verb.CREATE;
import static com.beachape.aminam.domain.authz.models.Verb.DELETE;
import static com.beachape.aminam.domain.authz.models.Verb.UPDATE;
import static java.time.ZoneOffset.UTC;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.beachape.aminam.domain.authc.models.AuthenticatedUser;
import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.authz.models.Policy;
import com.beachape.aminam.domain.authz.models.PolicyDocument;
import com.beachape.aminam.domain.authz.models.PolicyId.CustomPolicyId;
import com.beachape.aminam.domain.authz.models.ResourceRef;
import com.beachape.aminam.domain.authz.models.ResourceType;
import com.beachape.aminam.domain.authz.repositories.PolicyAttachmentRepository;
import com.beachape.aminam.domain.authz.repositories.PolicyRepository;
import com.beachape.aminam.domain.authz.services.PolicyValidator.InvalidPolicyException;
import com.beachape.aminam.domain.errors.NotAuthorisedException;
import com.beachape.aminam.domain.errors.NotFoundException;
import com.beachape.aminam.domain.orgs.models.Membership;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.beachape.aminam.domain.orgs.services.OrganisationService;
import com.beachape.aminam.domain.repositories.errors.EntityNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PolicyServiceTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-24T00:00:00Z"), UTC);
  private static final UserId USER = new UserId(randomUUID());
  private static final OrgId ORG = new OrgId(randomUUID());
  private static final ResourceRef ORG_REF =
      new ResourceRef.Existing(ResourceType.ORG, ORG.value());
  private static final ResourceRef TO_CREATE = new ResourceRef.ToCreate(POLICY, ORG);
  private static final AuthenticatedUser ACTOR =
      new AuthenticatedUser(
          USER, "lloyd", new Membership.UserMembership(new MembershipId(randomUUID()), ORG));

  private final PolicyRepository policies = mock();
  private final PolicyAttachmentRepository attachments = mock();
  private final PolicyValidator validator = mock();
  private final OrganisationService organisations = mock();
  private final GuardStub guard = new GuardStub();
  private final PolicyService service =
      new PolicyService(policies, attachments, validator, guard.authz(), organisations, CLOCK);

  @Test
  void createValidatesAndPersistsWhenAuthorised() throws Exception {
    guard.visible(ORG_REF);
    guard.permit(TO_CREATE, CREATE);
    when(policies.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var created = service.create(ACTOR, ORG, "reports", document());

    assertThat(created.name()).isEqualTo("reports");
    verify(validator).validate(document());
    verify(policies).create(any());
  }

  @Test
  void createDeniedSkipsValidationAndPersist() throws Exception {
    guard.visible(ORG_REF);
    guard.forbid(TO_CREATE, CREATE);

    assertThatExceptionOfType(NotAuthorisedException.class)
        .isThrownBy(() -> service.create(ACTOR, ORG, "reports", document()));
    verify(validator, never()).validate(any());
    verify(policies, never()).create(any());
  }

  @Test
  void createPropagatesValidationFailureWithoutPersisting() throws Exception {
    guard.visible(ORG_REF);
    guard.permit(TO_CREATE, CREATE);
    doThrow(new InvalidPolicyException(List.of())).when(validator).validate(any());

    assertThatExceptionOfType(InvalidPolicyException.class)
        .isThrownBy(() -> service.create(ACTOR, ORG, "reports", document()));
    verify(policies, never()).create(any());
  }

  @Test
  void createOnAnInvisibleOrgIsNotFound() throws Exception {
    guard.invisible(ORG_REF);

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.create(ACTOR, ORG, "reports", document()))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.ORGANISATION));
    verify(validator, never()).validate(any());
    verify(policies, never()).create(any());
  }

  @Test
  void getReturnsTheReadablePolicy() throws Exception {
    var policy = policy();
    guard.visible(policyRef(policy.id()));
    when(policies.findById(policy.id())).thenReturn(policy);

    assertThat(service.get(ACTOR, ORG, policy.id())).isEqualTo(policy);
  }

  @Test
  void getDeniedReadIsNotFound() {
    var policy = policy();
    guard.invisible(policyRef(policy.id()));

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.get(ACTOR, ORG, policy.id()))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.POLICY));
    verify(policies, never()).findById(any());
  }

  @Test
  void listKeepsOnlyTheReadablePolicies() throws Exception {
    var a = policy();
    var b = policy();
    when(policies.listByOrg(ORG)).thenReturn(List.of(a, b));
    guard.visible(policyRef(a.id()));
    guard.invisible(policyRef(b.id()));

    assertThat(service.list(ACTOR, ORG)).containsExactly(a);
  }

  @Test
  void updateDeniedActionIsForbiddenAndDoesNotValidateOrPersist() throws Exception {
    var policy = policy();
    guard.visible(ORG_REF);
    guard.visible(policyRef(policy.id()));
    guard.forbid(policyRef(policy.id()), UPDATE);

    assertThatExceptionOfType(NotAuthorisedException.class)
        .isThrownBy(() -> service.update(ACTOR, ORG, policy.id(), "new", document()));
    verify(validator, never()).validate(any());
    verify(policies, never()).update(any());
  }

  @Test
  void updateUnreadablePolicyIsNotFound() throws Exception {
    var policy = policy();
    guard.visible(ORG_REF);
    guard.invisible(policyRef(policy.id()));

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.update(ACTOR, ORG, policy.id(), "new", document()))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.POLICY));
    verify(policies, never()).update(any());
  }

  @Test
  void updateOrgInvisibleIsNotFound() throws Exception {
    var policy = policy();
    guard.invisible(ORG_REF);
    guard.visible(policyRef(policy.id()));
    guard.permit(policyRef(policy.id()), UPDATE);

    // The org gate is decided first, so an invisible org is a 404 flavoured ORGANISATION, not
    // POLICY.
    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.update(ACTOR, ORG, policy.id(), "new", document()))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.ORGANISATION));
    verify(policies, never()).update(any());
  }

  @Test
  void updateValidatesAndPersistsWhenAllowed() throws Exception {
    var policy = policy();
    guard.visible(ORG_REF);
    guard.visible(policyRef(policy.id()));
    guard.permit(policyRef(policy.id()), UPDATE);
    when(policies.findById(policy.id())).thenReturn(policy);
    when(policies.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var updated = service.update(ACTOR, ORG, policy.id(), "renamed", document());

    assertThat(updated.name()).isEqualTo("renamed");
    verify(validator).validate(document());
  }

  @Test
  void deleteRemovesThePolicyAndCascadesItsAttachments() throws Exception {
    var policy = policy();
    guard.visible(ORG_REF);
    guard.visible(policyRef(policy.id()));
    guard.permit(policyRef(policy.id()), DELETE);
    when(policies.findById(policy.id())).thenReturn(policy);

    service.delete(ACTOR, ORG, policy.id());

    verify(attachments).deleteByPolicyId(policy.id());
    verify(policies).delete(policy);
  }

  @Test
  void deleteDeniedActionIsForbidden() {
    var policy = policy();
    guard.visible(ORG_REF);
    guard.visible(policyRef(policy.id()));
    guard.forbid(policyRef(policy.id()), DELETE);

    assertThatExceptionOfType(NotAuthorisedException.class)
        .isThrownBy(() -> service.delete(ACTOR, ORG, policy.id()));
    verify(policies, never()).delete(any());
  }

  @Test
  void deleteUnreadablePolicyIsNotFound() {
    var policy = policy();
    guard.visible(ORG_REF);
    guard.invisible(policyRef(policy.id()));

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.delete(ACTOR, ORG, policy.id()))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.POLICY));
    verify(policies, never()).delete(any());
  }

  @Test
  void deleteOrgInvisibleIsNotFound() {
    var policy = policy();
    guard.invisible(ORG_REF);

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.delete(ACTOR, ORG, policy.id()))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.ORGANISATION));
    verify(policies, never()).delete(any());
  }

  @Test
  void updateMapsAVanishedRowToNotFound() throws Exception {
    var policy = policy();
    guard.visible(ORG_REF);
    guard.visible(policyRef(policy.id()));
    guard.permit(policyRef(policy.id()), UPDATE);
    when(policies.findById(policy.id())).thenReturn(policy);
    when(policies.update(any())).thenThrow(new EntityNotFoundException());

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.update(ACTOR, ORG, policy.id(), "renamed", document()))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.POLICY))
        .withCauseInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void updateVanishedBeforeFetchIsNotFound() throws Exception {
    var policy = policy();
    guard.visible(ORG_REF);
    guard.visible(policyRef(policy.id()));
    guard.permit(policyRef(policy.id()), UPDATE);
    when(policies.findById(policy.id())).thenReturn(null);

    // Gates pass, but the row is gone by fetch time: the explicit onMissing type must be POLICY.
    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.update(ACTOR, ORG, policy.id(), "renamed", document()))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.POLICY));
    verify(policies, never()).update(any());
  }

  @Test
  void deleteVanishedBeforeFetchIsNotFound() {
    var policy = policy();
    guard.visible(ORG_REF);
    guard.visible(policyRef(policy.id()));
    guard.permit(policyRef(policy.id()), DELETE);
    when(policies.findById(policy.id())).thenReturn(null);

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.delete(ACTOR, ORG, policy.id()))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.POLICY));
    verify(policies, never()).delete(any());
  }

  private static PolicyDocument document() {
    return new PolicyDocument(List.of());
  }

  private static Policy policy() {
    return new Policy(new CustomPolicyId(randomUUID()), ORG, "p", document(), CLOCK.instant());
  }

  private static ResourceRef policyRef(CustomPolicyId id) {
    return new ResourceRef.Existing(POLICY, id.value());
  }
}
