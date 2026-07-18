package com.beachape.aminam.domain.orgs.services;

import com.beachape.aminam.domain.authz.models.ResourceFacts;
import com.beachape.aminam.domain.authz.models.ResourceType;
import com.beachape.aminam.domain.authz.repositories.ResourceFactSource;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.beachape.aminam.domain.orgs.repositories.OrganisationRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Resolves an org resource to its facts. An org owns itself.
@ApplicationScoped
public class OrgFactSource implements ResourceFactSource {

  private final OrganisationRepository organisations;

  @Inject
  OrgFactSource(OrganisationRepository organisations) {
    this.organisations = organisations;
  }

  @Override
  public Set<ResourceType> types() {
    return Set.of(ResourceType.ORG);
  }

  @Override
  public @Nullable ResourceFacts resolve(UUID id) {
    var org = organisations.findById(new OrgId(id));
    return org == null ? null : new ResourceFacts(org.id());
  }
}
