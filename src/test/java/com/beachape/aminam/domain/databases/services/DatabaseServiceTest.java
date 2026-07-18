package com.beachape.aminam.domain.databases.services;

import static com.beachape.aminam.domain.authz.models.ResourceType.DATABASE;
import static com.beachape.aminam.domain.authz.models.Verb.ATTACH;
import static com.beachape.aminam.domain.authz.models.Verb.CREATE;
import static com.beachape.aminam.domain.authz.models.Verb.DELETE;
import static com.beachape.aminam.domain.authz.models.Verb.DETACH;
import static com.beachape.aminam.domain.authz.models.Verb.UPDATE;
import static java.time.ZoneOffset.UTC;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.beachape.aminam.domain.authc.models.AuthenticatedUser;
import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.authz.models.AttachmentPoint;
import com.beachape.aminam.domain.authz.models.AttachmentType;
import com.beachape.aminam.domain.authz.models.PolicyAttachment;
import com.beachape.aminam.domain.authz.models.PolicyId;
import com.beachape.aminam.domain.authz.models.PolicyId.CustomPolicyId;
import com.beachape.aminam.domain.authz.models.ResourceRef;
import com.beachape.aminam.domain.authz.models.ResourceType;
import com.beachape.aminam.domain.authz.repositories.PolicyAttachmentRepository;
import com.beachape.aminam.domain.authz.services.GuardStub;
import com.beachape.aminam.domain.authz.services.PolicyAuthzService;
import com.beachape.aminam.domain.authz.services.PolicyAuthzService.InvalidPoliciesException;
import com.beachape.aminam.domain.authz.services.PolicyAuthzService.InvalidPoliciesException.Failure;
import com.beachape.aminam.domain.databases.models.Database;
import com.beachape.aminam.domain.databases.models.DatabaseId;
import com.beachape.aminam.domain.databases.repositories.DatabaseRepository;
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

final class DatabaseServiceTest {

  private static final Clock FIXED = Clock.fixed(Instant.parse("2026-06-20T00:00:00Z"), UTC);
  private static final UserId USER = new UserId(randomUUID());
  private static final UserId CREATOR = new UserId(randomUUID());
  private static final MembershipId MEMBERSHIP = new MembershipId(randomUUID());
  private static final OrgId ORG = new OrgId(randomUUID());
  private static final ResourceRef ORG_REF =
      new ResourceRef.Existing(ResourceType.ORG, ORG.value());
  private static final ResourceRef TO_CREATE = new ResourceRef.ToCreate(DATABASE, ORG);

  private final DatabaseRepository databases = mock();
  private final OrganisationService organisations = mock();
  private final PolicyAttachmentRepository attachments = mock();
  private final PolicyAuthzService policyAuthz = mock();
  private final GuardStub guard = new GuardStub();
  private final DatabaseService service =
      new DatabaseService(databases, guard.authz(), organisations, attachments, policyAuthz, FIXED);

  @Test
  void createPersistsWhenCreateAllowed() throws Exception {
    guard.visible(ORG_REF);
    guard.permit(TO_CREATE, CREATE);
    when(databases.create(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var created = service.create(actor(), ORG, "metrics");

    assertThat(created.orgId()).isEqualTo(ORG);
    assertThat(created.name()).isEqualTo("metrics");
    assertThat(created.createdBy()).isEqualTo(USER);
    assertThat(created.createdAt()).isEqualTo(FIXED.instant());
    verify(databases).create(any());
  }

  @Test
  void createDeniedThrowsForbidden() {
    guard.visible(ORG_REF);
    guard.forbid(TO_CREATE, CREATE);

    assertThatExceptionOfType(NotAuthorisedException.class)
        .isThrownBy(() -> service.create(actor(), ORG, "metrics"));
    verify(databases, never()).create(any());
  }

  @Test
  void createThrowsNotFoundWhenOrgInvisible() {
    guard.invisible(ORG_REF);

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.create(actor(), ORG, "metrics"))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.ORGANISATION));
    verify(databases, never()).create(any());
  }

  @Test
  void listThrowsNotFoundWhenOrgInvisible() throws Exception {
    doThrow(new NotFoundException(NotFoundException.Type.ORGANISATION))
        .when(organisations)
        .requireOrgVisible(any(), eq(ORG));

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.list(actor(), ORG))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.ORGANISATION));
    verify(databases, never()).listByOrg(any());
  }

  @Test
  void getReturnsEditableTrueWhenUpdateAllowed() throws Exception {
    var database = db("metrics");
    guard.visible(ref(database));
    guard.permit(ref(database), UPDATE);
    when(databases.findById(database.id())).thenReturn(database);

    var visible = service.get(actor(), database.id());

    assertThat(visible.database()).isEqualTo(database);
    assertThat(visible.editable()).isTrue();
  }

  @Test
  void getReturnsEditableFalseWhenUpdateDenied() throws Exception {
    var database = db("metrics");
    guard.visible(ref(database));
    guard.forbid(ref(database), UPDATE);
    when(databases.findById(database.id())).thenReturn(database);

    assertThat(service.get(actor(), database.id()).editable()).isFalse();
  }

  @Test
  void getThrowsNotFoundWhenReadDenied() {
    var id = new DatabaseId(randomUUID());
    guard.invisible(refOf(id));

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.get(actor(), id))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.DATABASE));
    verify(databases, never()).findById(any());
  }

  @Test
  void getThrowsNotFoundWhenRowVanished() {
    var id = new DatabaseId(randomUUID());
    guard.visible(refOf(id));
    guard.permit(refOf(id), UPDATE);
    when(databases.findById(id)).thenReturn(null);

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.get(actor(), id))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.DATABASE));
  }

  @Test
  void updateNameWritesWhenEditable() throws Exception {
    var database = db("metrics");
    guard.visible(ref(database));
    guard.permit(ref(database), UPDATE);
    when(databases.findById(database.id())).thenReturn(database);
    when(databases.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var updated = service.updateName(actor(), database.id(), "renamed");

    assertThat(updated.name()).isEqualTo("renamed");
    assertThat(updated.id()).isEqualTo(database.id());
  }

  @Test
  void updateNamePreservesTheOriginalCreator() throws Exception {
    var database = db("metrics");
    guard.visible(ref(database));
    guard.permit(ref(database), UPDATE);
    when(databases.findById(database.id())).thenReturn(database);
    when(databases.update(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var updated = service.updateName(actor(), database.id(), "renamed");

    // A rename must not transfer authorship to the renamer: under a creator policy that would be an
    // escalation from update into read and delete.
    assertThat(updated.createdBy()).isEqualTo(CREATOR);
  }

  @Test
  void updateNameMapsAVanishedRowToNotFound() throws Exception {
    var database = db("metrics");
    guard.visible(ref(database));
    guard.permit(ref(database), UPDATE);
    when(databases.findById(database.id())).thenReturn(database);
    when(databases.update(any())).thenThrow(new EntityNotFoundException());

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.updateName(actor(), database.id(), "renamed"))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.DATABASE))
        .withCauseInstanceOf(EntityNotFoundException.class);
  }

  @Test
  void updateNameThrowsForbiddenWhenReadOnly() throws Exception {
    var id = new DatabaseId(randomUUID());
    guard.visible(refOf(id));
    guard.forbid(refOf(id), UPDATE);

    assertThatExceptionOfType(NotAuthorisedException.class)
        .isThrownBy(() -> service.updateName(actor(), id, "renamed"));
    verify(databases, never()).update(any());
    verify(databases, never()).findById(any());
  }

  @Test
  void updateNameThrowsNotFoundWhenUnavailable() throws Exception {
    var id = new DatabaseId(randomUUID());
    guard.invisible(refOf(id));

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.updateName(actor(), id, "renamed"))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.DATABASE));
    verify(databases, never()).update(any());
  }

  @Test
  void deleteRemovesTheRowAndItsResourcePolicies() throws Exception {
    var database = db("metrics");
    guard.visible(ref(database));
    guard.permit(ref(database), DELETE);
    when(databases.findById(database.id())).thenReturn(database);

    service.delete(actor(), database.id());

    verify(databases).delete(database);
    verify(attachments).deleteByPoint(pointFor(database.id()));
  }

  @Test
  void deleteThrowsForbiddenWhenReadOnly() {
    var id = new DatabaseId(randomUUID());
    guard.visible(refOf(id));
    guard.forbid(refOf(id), DELETE);

    assertThatExceptionOfType(NotAuthorisedException.class)
        .isThrownBy(() -> service.delete(actor(), id));
    verify(databases, never()).delete(any());
  }

  @Test
  void deleteThrowsNotFoundWhenUnavailable() {
    var id = new DatabaseId(randomUUID());
    guard.invisible(refOf(id));

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.delete(actor(), id))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.DATABASE));
    verify(databases, never()).delete(any());
  }

  @Test
  void listKeepsReadableRowsAndStampsEditability() throws Exception {
    var visibleEditable = db("a");
    var visibleReadOnly = db("b");
    var invisible = db("c");
    when(databases.listByOrg(ORG)).thenReturn(List.of(visibleEditable, visibleReadOnly, invisible));
    readable(visibleEditable, /* editable= */ true);
    readable(visibleReadOnly, /* editable= */ false);
    guard.invisible(ref(invisible));

    var listed = service.list(actor(), ORG);

    assertThat(listed)
        .extracting(v -> v.database(), v -> v.editable())
        .containsExactly(tuple(visibleEditable, true), tuple(visibleReadOnly, false));
  }

  @Test
  void setPoliciesAppliesTheDesiredSetAfterTheGate() throws Exception {
    var id = new DatabaseId(randomUUID());
    guard.visible(refOf(id));
    guard.permit(refOf(id), ATTACH);
    guard.permit(refOf(id), DETACH);
    var policyId = new CustomPolicyId(randomUUID());
    when(attachments.findByPoint(pointFor(id)))
        .thenReturn(List.of(new PolicyAttachment(pointFor(id), policyId)));

    var result = service.setPolicies(actor(), id, List.of(policyId));

    verify(policyAuthz).applyPolicies(any(), eq(pointFor(id)), eq(List.of(policyId)));
    assertThat(result).containsExactly(policyId);
  }

  @Test
  void setPoliciesThrowsForbiddenWhenAttachDenied() {
    var id = new DatabaseId(randomUUID());
    guard.visible(refOf(id));
    guard.forbid(refOf(id), ATTACH);
    guard.permit(refOf(id), DETACH);

    assertThatExceptionOfType(NotAuthorisedException.class)
        .isThrownBy(() -> service.setPolicies(actor(), id, List.of(somePolicy())));
    verifyNoInteractions(policyAuthz);
  }

  @Test
  void setPoliciesThrowsForbiddenWhenDetachDenied() {
    var id = new DatabaseId(randomUUID());
    guard.visible(refOf(id));
    guard.permit(refOf(id), ATTACH);
    guard.forbid(refOf(id), DETACH);

    assertThatExceptionOfType(NotAuthorisedException.class)
        .isThrownBy(() -> service.setPolicies(actor(), id, List.of(somePolicy())));
    verifyNoInteractions(policyAuthz);
  }

  @Test
  void setPoliciesThrowsNotFoundWhenDatabaseUnreadable() {
    var id = new DatabaseId(randomUUID());
    guard.invisible(refOf(id));

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.setPolicies(actor(), id, List.of(somePolicy())))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.DATABASE));
    verifyNoInteractions(policyAuthz);
  }

  @Test
  void setPoliciesPropagatesInvalidPolicies() throws Exception {
    var id = new DatabaseId(randomUUID());
    guard.visible(refOf(id));
    guard.permit(refOf(id), ATTACH);
    guard.permit(refOf(id), DETACH);
    var bad = somePolicy();
    doThrow(
            new InvalidPoliciesException(
                List.of(new Failure(bad, "unknown or not assignable policy"))))
        .when(policyAuthz)
        .applyPolicies(any(), any(), any());

    assertThatExceptionOfType(InvalidPoliciesException.class)
        .isThrownBy(() -> service.setPolicies(actor(), id, List.of(bad)));
  }

  @Test
  void listPoliciesReturnsTheAttachedIdsWhenReadable() throws Exception {
    var id = new DatabaseId(randomUUID());
    guard.visible(refOf(id));
    var policyId = new CustomPolicyId(randomUUID());
    when(attachments.findByPoint(pointFor(id)))
        .thenReturn(List.of(new PolicyAttachment(pointFor(id), policyId)));

    assertThat(service.listPolicies(actor(), id)).containsExactly(policyId);
  }

  @Test
  void listPoliciesThrowsNotFoundWhenUnreadable() {
    var id = new DatabaseId(randomUUID());
    guard.invisible(refOf(id));

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> service.listPolicies(actor(), id))
        .satisfies(e -> assertThat(e.type()).isEqualTo(NotFoundException.Type.DATABASE));
    verify(attachments, never()).findByPoint(any());
  }

  private void readable(Database database, boolean editable) {
    guard.visible(ref(database));
    if (editable) {
      guard.permit(ref(database), UPDATE);
    } else {
      guard.forbid(ref(database), UPDATE);
    }
  }

  private static ResourceRef ref(Database database) {
    return refOf(database.id());
  }

  private static ResourceRef refOf(DatabaseId id) {
    return new ResourceRef.Existing(DATABASE, id.value());
  }

  private static AttachmentPoint pointFor(DatabaseId id) {
    return new AttachmentPoint(AttachmentType.DATABASE, id.value());
  }

  private static PolicyId somePolicy() {
    return new CustomPolicyId(randomUUID());
  }

  /// Created by CREATOR, deliberately not the acting USER, so authorship assertions are meaningful.
  private static Database db(String name) {
    return new Database(new DatabaseId(randomUUID()), ORG, name, CREATOR, FIXED.instant());
  }

  private static AuthenticatedUser actor() {
    return new AuthenticatedUser(USER, "lloyd", new Membership.UserMembership(MEMBERSHIP, ORG));
  }
}
