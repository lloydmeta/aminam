package com.beachape.aminam.domain.databases.services;

import static com.beachape.aminam.domain.authz.models.ResourceType.DATABASE;
import static com.beachape.aminam.domain.authz.models.ResourceType.ORG;
import static com.beachape.aminam.domain.authz.models.Verb.ATTACH;
import static com.beachape.aminam.domain.authz.models.Verb.CREATE;
import static com.beachape.aminam.domain.authz.models.Verb.DELETE;
import static com.beachape.aminam.domain.authz.models.Verb.DETACH;
import static com.beachape.aminam.domain.authz.models.Verb.READ;
import static com.beachape.aminam.domain.authz.models.Verb.UPDATE;
import static java.util.UUID.randomUUID;

import com.beachape.aminam.domain.authc.models.AuthenticatedUser;
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
import com.beachape.aminam.domain.databases.models.Database;
import com.beachape.aminam.domain.databases.models.DatabaseId;
import com.beachape.aminam.domain.databases.models.VisibleDatabase;
import com.beachape.aminam.domain.databases.repositories.DatabaseRepository;
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

@ApplicationScoped
public class DatabaseService {

  private final DatabaseRepository databases;
  private final AuthorisationService authz;
  private final OrganisationService organisations;
  private final PolicyAttachmentRepository attachments;
  private final PolicyAuthzService policyAuthz;
  private final Clock clock;

  @Inject
  DatabaseService(
      DatabaseRepository databases,
      AuthorisationService authz,
      OrganisationService organisations,
      PolicyAttachmentRepository attachments,
      PolicyAuthzService policyAuthz,
      Clock clock) {
    this.databases = databases;
    this.authz = authz;
    this.organisations = organisations;
    this.attachments = attachments;
    this.policyAuthz = policyAuthz;
    this.clock = clock;
  }

  /// Creates a database in the org. The org must be visible (404 otherwise); then `database:create`
  /// gates it (403), which the regime confines to the active org.
  @Transactional(rollbackOn = DomainException.class)
  public Database create(AuthenticatedUser actor, OrgId orgId, String name)
      throws NotFoundException, NotAuthorisedException {
    authz
        .guard(actor)
        .visible(new ResourceRef.Existing(ORG, orgId.value()), NotFoundException.Type.ORGANISATION)
        .permit(new ResourceRef.ToCreate(DATABASE, orgId), CREATE)
        .check();
    return databases.create(
        new Database(new DatabaseId(randomUUID()), orgId, name, actor.id(), clock.instant()));
  }

  /// A database the principal may read, with its editability, or a 404 if not visible.
  public VisibleDatabase get(AuthenticatedUser actor, DatabaseId id) throws NotFoundException {
    var ref = new ResourceRef.Existing(DATABASE, id.value());
    var decisions =
        authz.checkAll(
            actor,
            List.of(
                new Check(new Action(DATABASE, READ), ref),
                new Check(new Action(DATABASE, UPDATE), ref)));
    if (!decisions.get(0).allowed()) {
      throw new NotFoundException(NotFoundException.Type.DATABASE);
    }
    var database = databases.findById(id);
    if (database == null) {
      throw new NotFoundException(NotFoundException.Type.DATABASE);
    }
    return new VisibleDatabase(database, decisions.get(1).allowed());
  }

  /// The org's databases the principal may read, each with its editability. The org must be visible
  /// (404 otherwise); a batch check then filters out rows the principal cannot read.
  public List<VisibleDatabase> list(AuthenticatedUser actor, OrgId orgId) throws NotFoundException {
    organisations.requireOrgVisible(actor, orgId);
    var rows = databases.listByOrg(orgId);
    var checks = new ArrayList<Check>(rows.size() * 2);
    for (var database : rows) {
      var ref = new ResourceRef.Existing(DATABASE, database.id().value());
      checks.add(new Check(new Action(DATABASE, READ), ref));
      checks.add(new Check(new Action(DATABASE, UPDATE), ref));
    }
    var decisions = authz.checkAll(actor, checks);
    var visible = new ArrayList<VisibleDatabase>();
    for (int i = 0; i < rows.size(); i++) {
      if (decisions.get(i * 2).allowed()) {
        visible.add(new VisibleDatabase(rows.get(i), decisions.get(i * 2 + 1).allowed()));
      }
    }
    return visible;
  }

  @Transactional(rollbackOn = DomainException.class)
  public Database updateName(AuthenticatedUser actor, DatabaseId id, String name)
      throws NotFoundException, NotAuthorisedException {
    var ref = new ResourceRef.Existing(DATABASE, id.value());
    var database =
        authz
            .guard(actor)
            .visible(ref, NotFoundException.Type.DATABASE)
            .permit(ref, UPDATE)
            .fetch(() -> databases.findById(id));
    try {
      // createdBy comes from the stored row, never the actor: a rename must not move authorship.
      return databases.update(
          new Database(
              database.id(), database.orgId(), name, database.createdBy(), database.createdAt()));
    } catch (EntityNotFoundException e) {
      // The row vanished between the read check and the write (concurrent delete) -> 404.
      throw new NotFoundException(NotFoundException.Type.DATABASE, e);
    }
  }

  @Transactional(rollbackOn = DomainException.class)
  public void delete(AuthenticatedUser actor, DatabaseId id)
      throws NotFoundException, NotAuthorisedException {
    var ref = new ResourceRef.Existing(DATABASE, id.value());
    var database =
        authz
            .guard(actor)
            .visible(ref, NotFoundException.Type.DATABASE)
            .permit(ref, DELETE)
            .fetch(() -> databases.findById(id));
    // Drop the database's resource (sharing) policies in the same transaction.
    attachments.deleteByPoint(pointFor(id));
    databases.delete(database);
  }

  /// The resource policies on a database. Read-first: an unreadable database is 404.
  /// (Its share list is never leaked.)
  public List<PolicyId> listPolicies(AuthenticatedUser actor, DatabaseId id)
      throws NotFoundException {
    var ref = new ResourceRef.Existing(DATABASE, id.value());
    if (!authz.check(actor, new Action(DATABASE, READ), ref).allowed()) {
      throw new NotFoundException(NotFoundException.Type.DATABASE);
    }
    return attachments.findByPoint(pointFor(id)).stream().map(PolicyAttachment::policyId).toList();
  }

  /// Replaces the database's resource policies with the desired set
  @Transactional(rollbackOn = DomainException.class)
  public List<PolicyId> setPolicies(AuthenticatedUser actor, DatabaseId id, List<PolicyId> desired)
      throws NotFoundException, NotAuthorisedException, InvalidPoliciesException {
    var ref = new ResourceRef.Existing(DATABASE, id.value());
    authz
        .guard(actor)
        .visible(ref, NotFoundException.Type.DATABASE)
        .permit(ref, ATTACH)
        .permit(ref, DETACH)
        .check();
    policyAuthz.applyPolicies(actor, pointFor(id), desired);
    return attachments.findByPoint(pointFor(id)).stream().map(PolicyAttachment::policyId).toList();
  }

  private static AttachmentPoint pointFor(DatabaseId id) {
    return new AttachmentPoint(AttachmentType.DATABASE, id.value());
  }
}
