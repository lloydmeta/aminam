package com.beachape.aminam.domain.authz.services;

import static com.beachape.aminam.domain.authz.models.Verb.READ;

import com.beachape.aminam.domain.authc.models.AuthenticatedUser;
import com.beachape.aminam.domain.authz.models.Action;
import com.beachape.aminam.domain.authz.models.AttachmentPoint;
import com.beachape.aminam.domain.authz.models.AttachmentType;
import com.beachape.aminam.domain.authz.models.AuthzDecision;
import com.beachape.aminam.domain.authz.models.AuthzDecision.Deny;
import com.beachape.aminam.domain.authz.models.AuthzPrincipal;
import com.beachape.aminam.domain.authz.models.EvaluationContext;
import com.beachape.aminam.domain.authz.models.PolicyDocument;
import com.beachape.aminam.domain.authz.models.PolicyId;
import com.beachape.aminam.domain.authz.models.ResourceFacts;
import com.beachape.aminam.domain.authz.models.ResourceRef;
import com.beachape.aminam.domain.authz.models.ResourceType;
import com.beachape.aminam.domain.authz.models.Verb;
import com.beachape.aminam.domain.authz.repositories.PolicyAttachmentRepository;
import com.beachape.aminam.domain.authz.repositories.PolicyRepository;
import com.beachape.aminam.domain.authz.repositories.ResourceFactSource;
import com.beachape.aminam.domain.errors.NotAuthorisedException;
import com.beachape.aminam.domain.errors.NotFoundException;
import com.beachape.aminam.domain.orgs.models.Membership;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/// The gather phase: the only authz I/O. Resolves the target resource's facts (per-type, via the
/// ResourceFactSource ports) and the active membership's identity policies, then hands a frozen
/// context to the pure PolicyEngine.
///
/// The active-membership lookup (identityPoliciesFor) stays here, not behind a fact source: it
/// is the session's identity anchor (which membership am I), not a per-type resource fact.
@ApplicationScoped
public class AuthorisationService {

  /// One action against one resource: the unit of a batch check.
  public record Check(Action action, ResourceRef resource) {}

  private static final Logger LOG = System.getLogger(AuthorisationService.class.getName());

  private final PolicyEngine engine;
  private final SystemPolicies systemPolicies;
  private final PolicyAttachmentRepository attachments;
  private final PolicyRepository policies;
  private final Map<ResourceType, ResourceFactSource> factSources;

  @Inject
  AuthorisationService(
      PolicyEngine engine,
      SystemPolicies systemPolicies,
      PolicyAttachmentRepository attachments,
      PolicyRepository policies,
      @Any Instance<ResourceFactSource> sources) {
    this.engine = engine;
    this.systemPolicies = systemPolicies;
    this.attachments = attachments;
    this.policies = policies;
    var map = new EnumMap<ResourceType, ResourceFactSource>(ResourceType.class);
    for (var source : sources) {
      for (var type : source.types()) {
        var previous = map.put(type, source);
        if (previous != null) {
          throw new IllegalStateException("two fact sources registered for " + type);
        }
      }
    }
    this.factSources = map;
  }

  /// Decides whether the principal may perform the action on the resource. A missing resource is a
  /// Deny so the caller can map it to 404 without leaking existence.
  public AuthzDecision check(AuthenticatedUser principal, Action action, ResourceRef resource) {
    return checkAll(principal, List.of(new Check(action, resource))).getFirst();
  }

  /// Decides a batch of checks for one principal, results in input order. Identity policies are
  /// gathered once and each distinct resource's facts once, so a read+write check or a per-item
  /// list check does not re-gather the shared inputs.
  public List<AuthzDecision> checkAll(AuthenticatedUser user, List<Check> checks) {
    var activeMembership = user.activeMembership();
    var identityPolicies = identityPoliciesFor(activeMembership);
    // simple caches; break if we want to go multi-thread but we're not there now.
    var factsByResource = new HashMap<ResourceRef, @Nullable ResourceFacts>();
    var resourcePoliciesByResource = new HashMap<ResourceRef, List<PolicyDocument>>();
    var results = new ArrayList<AuthzDecision>(checks.size());
    for (var check : checks) {
      var resource = check.resource();
      if (!factsByResource.containsKey(resource)) {
        factsByResource.put(resource, resolveFacts(resource));
      }
      var facts = factsByResource.get(resource);
      if (facts == null) {
        results.add(new Deny("resource not found"));
        continue;
      }
      var resourcePolicies =
          resourcePoliciesByResource.computeIfAbsent(resource, this::resourcePoliciesFor);
      var ctx =
          new EvaluationContext(
              new AuthzPrincipal(user.id(), activeMembership),
              check.action(),
              resource,
              facts,
              identityPolicies,
              resourcePolicies);
      results.add(engine.decide(ctx));
    }
    return results;
  }

  /// Starts a fail-closed authorisation guard for a principal. Declare the resources that must be
  /// visible (denied READ -> 404) and the actions that must be permitted (denied action -> 403),
  /// then terminate with check() or fetch(). All gates run in one gather, and every visibility is
  /// decided before any permit, so a 403 cannot surface ahead of an unmet 404.
  public Start guard(AuthenticatedUser actor) {
    return new Builder(this, actor, List.of(), List.of());
  }

  private @Nullable ResourceFacts resolveFacts(ResourceRef resource) {
    return switch (resource) {
      // A create has no row; its owning org is the request path's org.
      case ResourceRef.ToCreate create -> new ResourceFacts(create.owningOrg());
      case ResourceRef.Existing existing -> resolveExisting(existing);
    };
  }

  private @Nullable ResourceFacts resolveExisting(ResourceRef.Existing resource) {
    var source = factSources.get(resource.type());
    if (source == null) {
      throw new IllegalStateException(resource.type() + " has no fact source");
    }
    return source.resolve(resource.id());
  }

  /// Gathers the identity policies attached to the session's active membership. The membership id
  /// rides in the JWT (`mid` claim), so this needs no membership lookup: a kick deletes the
  /// membership's attachment rows in the same transaction, so a stale claim resolves to no policies
  /// and grants nothing (fail-closed), bounded by one token lifetime.
  private List<PolicyDocument> identityPoliciesFor(Membership.@Nullable UserMembership active) {
    if (active == null) {
      return List.of();
    }
    return policiesAt(new AttachmentPoint(AttachmentType.MEMBERSHIP, active.id().value()));
  }

  /// Gathers the resource policies attached to the target. Principal-agnostic: it loads
  /// every *resource* policy, and the *engine* uses the requester's active membership
  /// via each statement's `memberships` set to decide applicability.
  private List<PolicyDocument> resourcePoliciesFor(ResourceRef resource) {
    if (resource instanceof ResourceRef.Existing existing
        && existing.type() == ResourceType.DATABASE) {
      return policiesAt(new AttachmentPoint(AttachmentType.DATABASE, existing.id()));
    }
    return List.of();
  }

  /// Resolves every policy attached to a point into its document, skipping any that no longer
  /// resolve. Shared by the identity gather (the active membership's point) and the resource gather
  /// (the target database's point); which side a document lands on is positional, not a column.
  private List<PolicyDocument> policiesAt(AttachmentPoint point) {
    var documents = new ArrayList<PolicyDocument>();
    for (var attachment : attachments.findByPoint(point)) {
      var document = resolvePolicy(attachment.policyId());
      if (document == null) {
        LOG.log(
            Level.WARNING, "skipping unresolvable policy attachment {0}", attachment.policyId());
      } else {
        documents.add(document);
      }
    }
    return documents;
  }

  /// A system id resolves from the code catalogue; a custom id from the store. A deleted or unknown
  /// id resolves to null and is skipped, granting nothing.
  private @Nullable PolicyDocument resolvePolicy(PolicyId id) {
    return switch (id) {
      case PolicyId.SystemPolicyId system -> systemPolicies.find(system);
      case PolicyId.CustomPolicyId custom -> {
        var policy = policies.findById(custom);
        yield policy == null ? null : policy.document();
      }
    };
  }

  /// The guard is a small type-state builder. `visible(...)` declares 404-gated
  /// resources (a denied READ is a 404 of the given type); `permit(...)` declares
  /// 403-gated actions; a terminal (`check()`/`fetch(...)`) runs them. The stages
  /// track how many visibilities are declared so the null-mapping stays honest: with
  /// one visibility the no-arg `fetch` takes its 404 from that sole visibility; a
  /// second visibility leaves that stage, so a multi-visibility fetch must name its
  /// 404 type explicitly (the compiler enforces it).
  public sealed interface Start permits Builder {
    SingleRead visible(ResourceRef ref, NotFoundException.Type notFoundAs);
  }

  /// Exactly one visibility declared, still open. A second visibility moves to MultiRead; a permit
  /// moves to SingleActs (still one visibility).
  public sealed interface SingleRead extends SingleTerminal permits Builder {
    MultiRead visible(ResourceRef ref, NotFoundException.Type notFoundAs);

    SingleActs permit(ResourceRef ref, Verb verb);
  }

  /// Two or more visibilities declared, still open. The no-arg `fetch` is gone: a fetch here must
  /// name its 404 type, because "the last visibility" would be an ambiguous guess.
  public sealed interface MultiRead extends Terminal permits Builder {
    MultiRead visible(ResourceRef ref, NotFoundException.Type notFoundAs);

    MultiActs permit(ResourceRef ref, Verb verb);
  }

  /// One visibility, action stage: retains the no-arg `fetch` via SingleTerminal.
  public sealed interface SingleActs extends SingleTerminal permits Builder {
    SingleActs permit(ResourceRef ref, Verb verb);
  }

  /// Two or more visibilities, action stage: explicit-type fetch only.
  public sealed interface MultiActs extends Terminal permits Builder {
    MultiActs permit(ResourceRef ref, Verb verb);
  }

  /// Every terminal. `check()` runs the gates; `fetch(onMissing, ...)` also loads the target and
  /// maps a null (a concurrent delete after the gates passed) to a 404 of the given type.
  public sealed interface Terminal permits SingleTerminal, MultiRead, MultiActs {
    void check() throws NotFoundException, NotAuthorisedException;

    <T> T fetch(NotFoundException.Type onMissing, Supplier<@Nullable T> fetch)
        throws NotFoundException, NotAuthorisedException;
  }

  /// The single-visibility terminal. Adds the no-arg `fetch`: with one visibility the 404 type is
  /// unambiguous, so a null maps to that sole visibility's type without the caller naming it.
  public sealed interface SingleTerminal extends Terminal permits SingleRead, SingleActs {
    <T> T fetch(Supplier<@Nullable T> fetch) throws NotFoundException, NotAuthorisedException;
  }

  private record Visible(ResourceRef ref, NotFoundException.Type notFoundAs) {}

  private record Permit(ResourceRef ref, Verb verb) {}

  /// Immutable and copy-on-write: visible()/permit() return a NEW stage rather than mutating this,
  /// so an aliased earlier stage keeps its own gates. That is what makes the type-state airtight: a
  /// SingleRead reference always holds exactly one visibility, even under aliasing.
  static final class Builder implements Start, SingleRead, MultiRead, SingleActs, MultiActs {

    private final AuthorisationService authz;
    private final AuthenticatedUser actor;
    private final List<Visible> visibilities;
    private final List<Permit> permits;

    private Builder(
        AuthorisationService authz,
        AuthenticatedUser actor,
        List<Visible> visibilities,
        List<Permit> permits) {
      this.authz = authz;
      this.actor = actor;
      this.visibilities = visibilities;
      this.permits = permits;
    }

    // Immutable stage: returns a new Builder by design, not `this` (hence BuilderReturnThis off).
    @Override
    @SuppressWarnings("BuilderReturnThis")
    public Builder visible(ResourceRef ref, NotFoundException.Type notFoundAs) {
      return new Builder(
          authz, actor, appended(visibilities, new Visible(ref, notFoundAs)), permits);
    }

    // Immutable stage: returns a new Builder by design, not `this` (hence BuilderReturnThis off).
    @Override
    @SuppressWarnings("BuilderReturnThis")
    public Builder permit(ResourceRef ref, Verb verb) {
      return new Builder(authz, actor, visibilities, appended(permits, new Permit(ref, verb)));
    }

    @Override
    public void check() throws NotFoundException, NotAuthorisedException {
      gate();
    }

    @Override
    public <T> T fetch(Supplier<@Nullable T> fetch)
        throws NotFoundException, NotAuthorisedException {
      // Reachable only from a single-visibility stage, so getFirst() is the sole visibility.
      return fetch(visibilities.getFirst().notFoundAs(), fetch);
    }

    @Override
    public <T> T fetch(NotFoundException.Type onMissing, Supplier<@Nullable T> fetch)
        throws NotFoundException, NotAuthorisedException {
      gate();
      var entity = fetch.get();
      if (entity == null) {
        throw new NotFoundException(onMissing);
      }
      return entity;
    }

    /// One gather for every gate; all visibilities (404) are decided before any permit (403).
    private void gate() throws NotFoundException, NotAuthorisedException {
      var checks = new ArrayList<Check>(visibilities.size() + permits.size());
      for (var visible : visibilities) {
        checks.add(new Check(new Action(visible.ref().type(), READ), visible.ref()));
      }
      for (var permit : permits) {
        checks.add(new Check(new Action(permit.ref().type(), permit.verb()), permit.ref()));
      }
      var decisions = authz.checkAll(actor, checks);
      for (int i = 0; i < visibilities.size(); i++) {
        if (!decisions.get(i).allowed()) {
          throw new NotFoundException(visibilities.get(i).notFoundAs());
        }
      }
      for (int i = 0; i < permits.size(); i++) {
        if (!decisions.get(visibilities.size() + i).allowed()) {
          throw new NotAuthorisedException();
        }
      }
    }

    private static <E> List<E> appended(List<E> base, E element) {
      var next = new ArrayList<E>(base.size() + 1);
      next.addAll(base);
      next.add(element);
      return List.copyOf(next);
    }
  }
}
