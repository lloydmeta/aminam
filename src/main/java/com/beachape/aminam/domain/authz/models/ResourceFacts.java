package com.beachape.aminam.domain.authz.models;

import com.beachape.aminam.domain.orgs.models.OrgId;
import java.util.Map;

/// Server-resolved facts about the target resource, gathered before the pure decide phase. The
/// owning org drives the regime; attributes are the per-type values a CEL condition may read (a
/// database's name as resource.name), all server-resolved so a request can never forge them.
public record ResourceFacts(OrgId owningOrg, Map<String, String> attributes) {

  public ResourceFacts(OrgId owningOrg) {
    this(owningOrg, Map.of());
  }
}
