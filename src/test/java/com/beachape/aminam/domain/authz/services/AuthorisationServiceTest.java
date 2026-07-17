package com.beachape.aminam.domain.authz.services;

import static com.beachape.aminam.domain.authz.models.AttachmentType.MEMBERSHIP;
import static com.beachape.aminam.domain.authz.models.ResourceType.ORG;
import static com.beachape.aminam.domain.authz.models.Verb.READ;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import com.beachape.aminam.domain.authz.models.EvaluationContext;
import com.beachape.aminam.domain.authz.models.Policy;
import com.beachape.aminam.domain.authz.models.PolicyAttachment;
import com.beachape.aminam.domain.authz.models.PolicyDocument;
import com.beachape.aminam.domain.authz.models.PolicyId.CustomPolicyId;
import com.beachape.aminam.domain.authz.models.PolicyId.SystemPolicyId;
import com.beachape.aminam.domain.authz.models.ResourceFacts;
import com.beachape.aminam.domain.authz.models.ResourceRef;
import com.beachape.aminam.domain.authz.models.ResourceType;
import com.beachape.aminam.domain.authz.models.Verb;
import com.beachape.aminam.domain.authz.repositories.PolicyAttachmentRepository;
import com.beachape.aminam.domain.authz.repositories.PolicyRepository;
import com.beachape.aminam.domain.authz.repositories.ResourceFactSource;
import com.beachape.aminam.domain.authz.services.AuthorisationService.Check;
import com.beachape.aminam.domain.errors.NotAuthorisedException;
import com.beachape.aminam.domain.errors.NotFoundException;
import com.beachape.aminam.domain.orgs.models.Membership;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import jakarta.enterprise.inject.Instance;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class AuthorisationServiceTest {

  private static final UserId USER = new UserId(randomUUID());
  private static final OrgId ORG_X = new OrgId(randomUUID());
  private static final ResourceRef ORG_REF = new ResourceRef.Existing(ORG, ORG_X.value());
  private static final MembershipId MEMBERSHIP_ID = new MembershipId(randomUUID());
  private static final ResourceRef MEMBERSHIP_REF =
      new ResourceRef.Existing(ResourceType.MEMBERSHIP, MEMBERSHIP_ID.value());
  private static final AttachmentPoint POINT =
      new AttachmentPoint(MEMBERSHIP, MEMBERSHIP_ID.value());
  private static final Action READ_ORG = new Action(ORG, READ);
  private static final UUID DB = randomUUID();
  private static final ResourceRef DB_REF = new ResourceRef.Existing(ResourceType.DATABASE, DB);
  private static final AttachmentPoint DB_POINT = new AttachmentPoint(AttachmentType.DATABASE, DB);
  private static final Action READ_DB = new Action(ResourceType.DATABASE, READ);
  private static final PolicyDocument RESOLVED_DOC = new PolicyDocument(List.of());
  private static final Instant INSTANT = Instant.parse("2026-06-24T00:00:00Z");

  private final PolicyEngine engine = mock();
  private final SystemPolicies systemPolicies = mock();
  private final PolicyAttachmentRepository attachments = mock();
  private final PolicyRepository policies = mock();
  private final ResourceFactSource orgSource = mock();
  private final ResourceFactSource membershipSource = mock();
  private final ResourceFactSource databaseSource = mock();

  private AuthorisationService service;

  @BeforeEach
  void setUp() {
    when(orgSource.types()).thenReturn(Set.of(ORG));
    when(membershipSource.types())
        .thenReturn(Set.of(ResourceType.MEMBERSHIP, ResourceType.SELF_MEMBERSHIP));
    when(databaseSource.types()).thenReturn(Set.of(ResourceType.DATABASE));
    service = newService(orgSource, membershipSource, databaseSource);
  }

  @Test
  void missingResourceDeniesBeforeDeciding() {
    when(orgSource.resolve(ORG_X.value())).thenReturn(null);

    var decision = service.check(user(ORG_X), READ_ORG, ORG_REF);

    assertThat(decision).isInstanceOf(Deny.class);
    verifyNoInteractions(engine);
  }

  @Test
  void gathersOwningOrgAndIdentityPolicies() {
    foundOrg();
    seatedManager();
    when(engine.decide(any())).thenReturn(new Allow("ok"));

    service.check(user(ORG_X), READ_ORG, ORG_REF);

    var ctx = capturedContext();
    assertThat(ctx.principal().id()).isEqualTo(USER);
    assertThat(ctx.principal().activeMembership())
        .isEqualTo(new Membership.UserMembership(MEMBERSHIP_ID, ORG_X));
    assertThat(ctx.principal().activeOrg()).isEqualTo(ORG_X);
    assertThat(ctx.resourceFacts().owningOrg()).isEqualTo(ORG_X);
    assertThat(ctx.identityPolicies()).containsExactly(RESOLVED_DOC);
    assertThat(ctx.resourcePolicies()).isEmpty();
  }

  @Test
  void orgLessPrincipalGathersNoIdentityPolicies() {
    foundOrg();
    when(engine.decide(any())).thenReturn(new Deny("no"));

    service.check(user(null), READ_ORG, ORG_REF);

    assertThat(capturedContext().identityPolicies()).isEmpty();
    verifyNoInteractions(attachments);
  }

  @Test
  void kickedMembershipWithNoAttachmentsGathersNoIdentityPolicies() {
    // A kick deletes the membership's attachment rows, so a stale `mid` claim resolves to no
    // identity policies and grants nothing (fail-closed), with no membership lookup.
    foundOrg();
    when(attachments.findByPoint(POINT)).thenReturn(List.of());
    when(engine.decide(any())).thenReturn(new Deny("no"));

    service.check(user(ORG_X), READ_ORG, ORG_REF);

    assertThat(capturedContext().identityPolicies()).isEmpty();
  }

  @Test
  void customPolicyAttachmentResolvesViaTheRepository() {
    foundOrg();
    var custom = new CustomPolicyId(randomUUID());
    when(attachments.findByPoint(POINT)).thenReturn(List.of(new PolicyAttachment(POINT, custom)));
    when(policies.findById(custom))
        .thenReturn(new Policy(custom, ORG_X, "p", RESOLVED_DOC, INSTANT));
    when(engine.decide(any())).thenReturn(new Allow("ok"));

    service.check(user(ORG_X), READ_ORG, ORG_REF);

    assertThat(capturedContext().identityPolicies()).containsExactly(RESOLVED_DOC);
  }

  @Test
  void unresolvablePolicyAttachmentIsSkipped() {
    foundOrg();
    var bogus = new SystemPolicyId("system:bogus");
    when(attachments.findByPoint(POINT))
        .thenReturn(
            List.of(
                new PolicyAttachment(POINT, bogus),
                new PolicyAttachment(POINT, SystemPolicies.MANAGER)));
    when(systemPolicies.find(bogus)).thenReturn(null);
    when(systemPolicies.find(SystemPolicies.MANAGER)).thenReturn(RESOLVED_DOC);
    when(engine.decide(any())).thenReturn(new Allow("ok"));

    service.check(user(ORG_X), READ_ORG, ORG_REF);

    assertThat(capturedContext().identityPolicies()).containsExactly(RESOLVED_DOC);
  }

  @Test
  void gathersResourcePoliciesForADatabase() {
    when(databaseSource.resolve(DB)).thenReturn(new ResourceFacts(ORG_X));
    var custom = new CustomPolicyId(randomUUID());
    when(attachments.findByPoint(DB_POINT))
        .thenReturn(List.of(new PolicyAttachment(DB_POINT, custom)));
    when(policies.findById(custom))
        .thenReturn(new Policy(custom, ORG_X, "share", RESOLVED_DOC, INSTANT));
    when(engine.decide(any())).thenReturn(new Allow("ok"));

    service.check(user(ORG_X), READ_DB, DB_REF);

    assertThat(capturedContext().resourcePolicies()).containsExactly(RESOLVED_DOC);
  }

  @Test
  void gathersResourcePoliciesOncePerResourceAcrossABatch() {
    when(databaseSource.resolve(DB)).thenReturn(new ResourceFacts(ORG_X));
    when(engine.decide(any())).thenReturn(new Allow("ok"));

    service.checkAll(
        user(ORG_X),
        List.of(
            new Check(READ_DB, DB_REF),
            new Check(new Action(ResourceType.DATABASE, Verb.UPDATE), DB_REF)));

    verify(attachments, times(1)).findByPoint(DB_POINT);
  }

  @Test
  void toCreateUsesPathOrgFactsWithoutAResourceLookup() {
    seatedManager();
    when(engine.decide(any())).thenReturn(new Allow("ok"));

    var action = new Action(ResourceType.MEMBERSHIP, Verb.CREATE);
    service.check(user(ORG_X), action, new ResourceRef.ToCreate(ResourceType.MEMBERSHIP, ORG_X));

    var ctx = capturedContext();
    assertThat(ctx.action()).isEqualTo(action);
    assertThat(ctx.resource().type()).isEqualTo(ResourceType.MEMBERSHIP);
    assertThat(ctx.resourceFacts().owningOrg()).isEqualTo(ORG_X);
    assertThat(ctx.identityPolicies()).containsExactly(RESOLVED_DOC);
    verify(orgSource, never()).resolve(any());
    verify(membershipSource, never()).resolve(any());
  }

  @Test
  void existingMembershipResolvesItsOwningOrgViaItsSource() {
    when(membershipSource.resolve(MEMBERSHIP_ID.value())).thenReturn(new ResourceFacts(ORG_X));
    when(engine.decide(any())).thenReturn(new Allow("ok"));

    service.check(user(ORG_X), new Action(ResourceType.MEMBERSHIP, Verb.DELETE), MEMBERSHIP_REF);

    assertThat(capturedContext().resourceFacts().owningOrg()).isEqualTo(ORG_X);
  }

  @Test
  void missingMembershipDeniesBeforeDeciding() {
    when(membershipSource.resolve(MEMBERSHIP_ID.value())).thenReturn(null);

    var decision =
        service.check(
            user(ORG_X), new Action(ResourceType.MEMBERSHIP, Verb.DELETE), MEMBERSHIP_REF);

    assertThat(decision).isInstanceOf(Deny.class);
    verifyNoInteractions(engine);
  }

  @Test
  void checkAllReturnsResultsInInputOrder() {
    foundOrg();
    seatedManager();
    when(engine.decide(any())).thenReturn(new Allow("read"), new Deny("update"));

    var decisions =
        service.checkAll(
            user(ORG_X),
            List.of(
                new Check(READ_ORG, ORG_REF), new Check(new Action(ORG, Verb.UPDATE), ORG_REF)));

    assertThat(decisions.get(0)).isInstanceOf(Allow.class);
    assertThat(decisions.get(1)).isInstanceOf(Deny.class);
  }

  @Test
  void checkAllGathersSharedResourceAndIdentityOnce() {
    foundOrg();
    seatedManager();
    when(engine.decide(any())).thenReturn(new Allow("ok"));

    service.checkAll(
        user(ORG_X),
        List.of(new Check(READ_ORG, ORG_REF), new Check(new Action(ORG, Verb.UPDATE), ORG_REF)));

    verify(orgSource, times(1)).resolve(ORG_X.value());
    verify(attachments, times(1)).findByPoint(POINT);
  }

  @Test
  void unregisteredResourceTypeThrows() {
    var orgOnly = newService(orgSource);

    assertThatThrownBy(
            () ->
                orgOnly.check(
                    user(ORG_X), new Action(ResourceType.MEMBERSHIP, Verb.DELETE), MEMBERSHIP_REF))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("MEMBERSHIP");
  }

  @Test
  void twoSourcesForTheSameTypeFailAtConstruction() {
    ResourceFactSource otherOrgSource = mock();
    when(otherOrgSource.types()).thenReturn(Set.of(ORG));

    assertThatThrownBy(() -> newService(orgSource, otherOrgSource))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ORG");
  }

  @Test
  void guardFetchReturnsTheEntityWhenAllGatesPass() throws Exception {
    when(databaseSource.resolve(DB)).thenReturn(new ResourceFacts(ORG_X));
    when(engine.decide(any())).thenReturn(new Allow("read"), new Allow("update"));

    var loaded =
        service
            .guard(user(ORG_X))
            .visible(DB_REF, NotFoundException.Type.DATABASE)
            .permit(DB_REF, Verb.UPDATE)
            .fetch(() -> "loaded");

    assertThat(loaded).isEqualTo("loaded");
  }

  @Test
  void guardReadDenyIsReportedAsNotFound() {
    when(databaseSource.resolve(DB)).thenReturn(new ResourceFacts(ORG_X));
    when(engine.decide(any())).thenReturn(new Deny("read"), new Allow("update"));

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(
            () ->
                service
                    .guard(user(ORG_X))
                    .visible(DB_REF, NotFoundException.Type.DATABASE)
                    .permit(DB_REF, Verb.UPDATE)
                    .check())
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.DATABASE));
  }

  @Test
  void guardActionDenyIsNotAuthorised() {
    when(databaseSource.resolve(DB)).thenReturn(new ResourceFacts(ORG_X));
    when(engine.decide(any())).thenReturn(new Allow("read"), new Deny("update"));

    assertThatExceptionOfType(NotAuthorisedException.class)
        .isThrownBy(
            () ->
                service
                    .guard(user(ORG_X))
                    .visible(DB_REF, NotFoundException.Type.DATABASE)
                    .permit(DB_REF, Verb.UPDATE)
                    .check());
  }

  @Test
  void guardFailingVisibilityBeatsFailingPermit() {
    // Both the READ and the action are denied; the guard must surface the 404, never the 403, so a
    // 403-vs-404 oracle cannot leak the resource's existence.
    when(databaseSource.resolve(DB)).thenReturn(new ResourceFacts(ORG_X));
    when(engine.decide(any())).thenReturn(new Deny("read"), new Deny("update"));

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(
            () ->
                service
                    .guard(user(ORG_X))
                    .visible(DB_REF, NotFoundException.Type.DATABASE)
                    .permit(DB_REF, Verb.UPDATE)
                    .check())
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.DATABASE));
  }

  @Test
  void guardFetchNullMapsToTheSoleVisibilitysType() {
    when(databaseSource.resolve(DB)).thenReturn(new ResourceFacts(ORG_X));
    when(engine.decide(any())).thenReturn(new Allow("db"));

    // A single-visibility guard needs no explicit type: the no-arg fetch takes the sole
    // visibility's.
    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(
            () ->
                service
                    .guard(user(ORG_X))
                    .visible(DB_REF, NotFoundException.Type.DATABASE)
                    .fetch(() -> null))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.DATABASE));
  }

  @Test
  void guardFetchNullMapsToTheExplicitTypeNotTheLastVisibility() {
    foundOrg();
    when(databaseSource.resolve(DB)).thenReturn(new ResourceFacts(ORG_X));
    when(engine.decide(any())).thenReturn(new Allow("org"), new Allow("db"));

    // DATABASE is the last-declared visibility, but the explicit type wins: a revert to a
    // getLast()-style guess would surface DATABASE and fail here.
    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(
            () ->
                service
                    .guard(user(ORG_X))
                    .visible(ORG_REF, NotFoundException.Type.ORGANISATION)
                    .visible(DB_REF, NotFoundException.Type.DATABASE)
                    .fetch(NotFoundException.Type.ORGANISATION, () -> null))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.ORGANISATION));
  }

  @Test
  void guardRunsAllGatesInOneGather() throws Exception {
    foundOrg();
    when(databaseSource.resolve(DB)).thenReturn(new ResourceFacts(ORG_X));
    when(engine.decide(any())).thenReturn(new Allow("ok"));

    service
        .guard(user(ORG_X))
        .visible(ORG_REF, NotFoundException.Type.ORGANISATION)
        .visible(DB_REF, NotFoundException.Type.DATABASE)
        .permit(DB_REF, Verb.UPDATE)
        .check();

    // One gather: identity policies resolve exactly once and all three gates decide in one batch.
    verify(attachments, times(1)).findByPoint(POINT);
    verify(engine, times(3)).decide(any());
  }

  @Test
  void guardFirstFailingVisibilityIsReportedWithItsOwnType() {
    foundOrg();
    when(databaseSource.resolve(DB)).thenReturn(new ResourceFacts(ORG_X));
    // Org READ denied, db READ allowed: the 404 must carry the FIRST failing gate's type, not the
    // last visibility's.
    when(engine.decide(any())).thenReturn(new Deny("org"), new Allow("db"));

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(
            () ->
                service
                    .guard(user(ORG_X))
                    .visible(ORG_REF, NotFoundException.Type.ORGANISATION)
                    .visible(DB_REF, NotFoundException.Type.DATABASE)
                    .check())
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.ORGANISATION));
  }

  @Test
  void guardSecondFailingPermitIsNotAuthorised() {
    when(databaseSource.resolve(DB)).thenReturn(new ResourceFacts(ORG_X));
    // READ allowed, first action allowed, second action denied: every permit must pass -> 403.
    when(engine.decide(any()))
        .thenReturn(new Allow("read"), new Allow("attach"), new Deny("detach"));

    assertThatExceptionOfType(NotAuthorisedException.class)
        .isThrownBy(
            () ->
                service
                    .guard(user(ORG_X))
                    .visible(DB_REF, NotFoundException.Type.DATABASE)
                    .permit(DB_REF, Verb.ATTACH)
                    .permit(DB_REF, Verb.DETACH)
                    .check());
  }

  @Test
  void guardMissingResourceIsReportedAsNotFound() {
    // The fact source resolves nothing: checkAll denies before the engine, and the guard maps that
    // to a 404, hiding whether the resource exists.
    when(databaseSource.resolve(DB)).thenReturn(null);

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(
            () ->
                service
                    .guard(user(ORG_X))
                    .visible(DB_REF, NotFoundException.Type.DATABASE)
                    .permit(DB_REF, Verb.UPDATE)
                    .check())
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.DATABASE));
    verifyNoInteractions(engine);
  }

  @Test
  void guardCreateGatePermitsOnAToCreateRef() throws Exception {
    foundOrg();
    when(engine.decide(any())).thenReturn(new Allow("org"), new Allow("create"));

    service
        .guard(user(ORG_X))
        .visible(ORG_REF, NotFoundException.Type.ORGANISATION)
        .permit(new ResourceRef.ToCreate(ResourceType.DATABASE, ORG_X), Verb.CREATE)
        .check();

    // A ToCreate resolves its owning-org facts from the path, with no resource lookup.
    verify(databaseSource, never()).resolve(any());
  }

  private void foundOrg() {
    when(orgSource.resolve(ORG_X.value())).thenReturn(new ResourceFacts(ORG_X));
  }

  private void seatedManager() {
    when(attachments.findByPoint(POINT))
        .thenReturn(List.of(new PolicyAttachment(POINT, SystemPolicies.MANAGER)));
    when(systemPolicies.find(SystemPolicies.MANAGER)).thenReturn(RESOLVED_DOC);
  }

  private AuthorisationService newService(ResourceFactSource... sources) {
    return new AuthorisationService(
        engine, systemPolicies, attachments, policies, instance(sources));
  }

  private EvaluationContext capturedContext() {
    var captor = ArgumentCaptor.forClass(EvaluationContext.class);
    verify(engine).decide(captor.capture());
    return captor.getValue();
  }

  private static AuthenticatedUser user(@Nullable OrgId activeOrg) {
    var active = activeOrg == null ? null : new Membership.UserMembership(MEMBERSHIP_ID, activeOrg);
    return new AuthenticatedUser(USER, "lloyd", active);
  }

  private static Instance<ResourceFactSource> instance(ResourceFactSource... sources) {
    Instance<ResourceFactSource> instance = mock();
    when(instance.iterator()).thenAnswer(invocation -> List.of(sources).iterator());
    return instance;
  }
}
