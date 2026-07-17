package com.beachape.aminam.domain.authz.services;

import static com.beachape.aminam.domain.authz.models.ResourceType.POLICY;
import static com.beachape.aminam.domain.authz.models.Verb.READ;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.beachape.aminam.domain.authc.models.AuthenticatedUser;
import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.authz.models.Action;
import com.beachape.aminam.domain.authz.models.AttachmentPoint;
import com.beachape.aminam.domain.authz.models.AttachmentType;
import com.beachape.aminam.domain.authz.models.AuthzDecision.Allow;
import com.beachape.aminam.domain.authz.models.AuthzDecision.Deny;
import com.beachape.aminam.domain.authz.models.PolicyAttachment;
import com.beachape.aminam.domain.authz.models.PolicyId.CustomPolicyId;
import com.beachape.aminam.domain.authz.models.PolicyId.SystemPolicyId;
import com.beachape.aminam.domain.authz.models.ResourceRef;
import com.beachape.aminam.domain.authz.repositories.PolicyAttachmentRepository;
import com.beachape.aminam.domain.authz.services.PolicyAuthzService.InvalidPoliciesException;
import com.beachape.aminam.domain.orgs.models.Membership;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PolicyAuthzServiceTest {

  private static final AuthenticatedUser ACTOR =
      new AuthenticatedUser(
          new UserId(randomUUID()),
          "lloyd",
          new Membership.UserMembership(new MembershipId(randomUUID()), new OrgId(randomUUID())));
  private static final AttachmentPoint POINT =
      new AttachmentPoint(AttachmentType.DATABASE, randomUUID());

  private final SystemPolicies systemPolicies = new SystemPolicies();
  private final AuthorisationService authz = mock();
  private final PolicyAttachmentRepository attachments = mock();
  private final PolicyAuthzService service =
      new PolicyAuthzService(systemPolicies, authz, attachments);

  @Test
  void systemPolicyAssignableWhenTheCatalogueAllowsIt() {
    assertThat(service.assignable(ACTOR, SystemPolicies.MANAGER)).isTrue();
    verifyNoInteractions(authz);
  }

  @Test
  void systemPolicyNotAssignableWhenTheCatalogueRefusesIt() {
    assertThat(service.assignable(ACTOR, new SystemPolicyId("system:superadmin"))).isFalse();
    verifyNoInteractions(authz);
  }

  @Test
  void customPolicyAssignableWhenPolicyReadAllows() {
    var id = new CustomPolicyId(randomUUID());
    when(authz.check(ACTOR, new Action(POLICY, READ), new ResourceRef.Existing(POLICY, id.value())))
        .thenReturn(new Allow("ok"));

    assertThat(service.assignable(ACTOR, id)).isTrue();
  }

  @Test
  void customPolicyNotAssignableWhenPolicyReadDenies() {
    var id = new CustomPolicyId(randomUUID());
    when(authz.check(any(), any(), any())).thenReturn(new Deny("no"));

    assertThat(service.assignable(ACTOR, id)).isFalse();
  }

  @Test
  void applyPoliciesAttachesTheAddedAndDetachesTheDropped() throws Exception {
    when(attachments.findByPoint(POINT))
        .thenReturn(List.of(new PolicyAttachment(POINT, SystemPolicies.VIEWER)));

    service.applyPolicies(ACTOR, POINT, List.of(SystemPolicies.ADMIN));

    verify(attachments).attach(new PolicyAttachment(POINT, SystemPolicies.ADMIN));
    verify(attachments).detach(new PolicyAttachment(POINT, SystemPolicies.VIEWER));
  }

  @Test
  void applyPoliciesIsIdempotentForTheSameSet() throws Exception {
    when(attachments.findByPoint(POINT))
        .thenReturn(List.of(new PolicyAttachment(POINT, SystemPolicies.VIEWER)));

    service.applyPolicies(ACTOR, POINT, List.of(SystemPolicies.VIEWER));

    verify(attachments, never()).attach(any());
    verify(attachments, never()).detach(any());
  }

  @Test
  void applyPoliciesWithAnEmptySetDetachesEverything() throws Exception {
    when(attachments.findByPoint(POINT))
        .thenReturn(
            List.of(
                new PolicyAttachment(POINT, SystemPolicies.VIEWER),
                new PolicyAttachment(POINT, SystemPolicies.ADMIN)));

    service.applyPolicies(ACTOR, POINT, List.of());

    verify(attachments, never()).attach(any());
    verify(attachments).detach(new PolicyAttachment(POINT, SystemPolicies.VIEWER));
    verify(attachments).detach(new PolicyAttachment(POINT, SystemPolicies.ADMIN));
  }

  @Test
  void applyPoliciesRejectsAnUnassignableIdAtomically() {
    var bad = new SystemPolicyId("system:superadmin");

    assertThatExceptionOfType(InvalidPoliciesException.class)
        .isThrownBy(() -> service.applyPolicies(ACTOR, POINT, List.of(SystemPolicies.ADMIN, bad)))
        .satisfies(
            ex ->
                assertThat(ex.failures())
                    .extracting(f -> f.policyId().asText())
                    .containsExactly("system:superadmin"));
    verifyNoInteractions(attachments);
  }

  @Test
  void applyPoliciesRejectsAForeignCustomPolicy() {
    var foreign = new CustomPolicyId(randomUUID());
    when(authz.check(any(), any(), any())).thenReturn(new Deny("cross-org"));

    assertThatExceptionOfType(InvalidPoliciesException.class)
        .isThrownBy(() -> service.applyPolicies(ACTOR, POINT, List.of(foreign)))
        .satisfies(
            ex ->
                assertThat(ex.failures())
                    .extracting(f -> f.policyId().asText())
                    .containsExactly(foreign.asText()));
    verify(attachments, never()).attach(any());
    verify(attachments, never()).detach(any());
  }
}
