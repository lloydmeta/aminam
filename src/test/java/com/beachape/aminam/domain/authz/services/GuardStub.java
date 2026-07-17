package com.beachape.aminam.domain.authz.services;

import static com.beachape.aminam.domain.authz.models.Verb.READ;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.beachape.aminam.domain.authc.models.AuthenticatedUser;
import com.beachape.aminam.domain.authz.models.Action;
import com.beachape.aminam.domain.authz.models.AuthzDecision;
import com.beachape.aminam.domain.authz.models.AuthzDecision.Allow;
import com.beachape.aminam.domain.authz.models.AuthzDecision.Deny;
import com.beachape.aminam.domain.authz.models.ResourceRef;
import com.beachape.aminam.domain.authz.models.Verb;
import com.beachape.aminam.domain.authz.repositories.PolicyAttachmentRepository;
import com.beachape.aminam.domain.authz.repositories.PolicyRepository;
import com.beachape.aminam.domain.authz.repositories.ResourceFactSource;
import com.beachape.aminam.domain.authz.services.AuthorisationService.Check;
import jakarta.enterprise.inject.Instance;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// A stub AuthorisationService: the real guard()/Builder run, but the gather is a per-Check decision
// table (unstubbed -> Deny). In this package to reach the package-private constructor.
public final class GuardStub {

  private final Map<Check, AuthzDecision> decisions = new HashMap<>();
  private final AuthorisationService authz;

  public GuardStub() {
    Instance<ResourceFactSource> noSources = mock();
    when(noSources.iterator()).thenAnswer(invocation -> List.<ResourceFactSource>of().iterator());
    this.authz =
        new AuthorisationService(
            mock(PolicyEngine.class),
            mock(SystemPolicies.class),
            mock(PolicyAttachmentRepository.class),
            mock(PolicyRepository.class),
            noSources) {
          @Override
          public List<AuthzDecision> checkAll(AuthenticatedUser user, List<Check> checks) {
            return checks.stream()
                .map(check -> decisions.getOrDefault(check, new Deny("no")))
                .toList();
          }
        };
  }

  public AuthorisationService authz() {
    return authz;
  }

  public void visible(ResourceRef ref) {
    decide(ref, READ, new Allow("ok"));
  }

  public void invisible(ResourceRef ref) {
    decide(ref, READ, new Deny("no"));
  }

  public void permit(ResourceRef ref, Verb verb) {
    decide(ref, verb, new Allow("ok"));
  }

  public void forbid(ResourceRef ref, Verb verb) {
    decide(ref, verb, new Deny("no"));
  }

  private void decide(ResourceRef ref, Verb verb, AuthzDecision decision) {
    decisions.put(new Check(new Action(ref.type(), verb), ref), decision);
  }
}
