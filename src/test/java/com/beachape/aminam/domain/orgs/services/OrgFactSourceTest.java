package com.beachape.aminam.domain.orgs.services;

import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.authz.models.ResourceType;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.beachape.aminam.domain.orgs.models.Organisation;
import com.beachape.aminam.domain.orgs.repositories.OrganisationRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;

final class OrgFactSourceTest {

  private final OrganisationRepository organisations = mock();
  private final OrgFactSource source = new OrgFactSource(organisations);

  @Test
  void resolvesOwningOrgAsTheOrgItself() {
    var id = randomUUID();
    when(organisations.findById(new OrgId(id)))
        .thenReturn(
            new Organisation(new OrgId(id), "acme", new UserId(randomUUID()), Instant.EPOCH));

    assertThat(requireNonNull(source.resolve(id)).owningOrg()).isEqualTo(new OrgId(id));
  }

  @Test
  void returnsNullWhenAbsent() {
    var id = randomUUID();
    when(organisations.findById(new OrgId(id))).thenReturn(null);

    assertThat(source.resolve(id)).isNull();
  }

  @Test
  void declaresOrgType() {
    assertThat(source.types()).containsExactly(ResourceType.ORG);
  }
}
