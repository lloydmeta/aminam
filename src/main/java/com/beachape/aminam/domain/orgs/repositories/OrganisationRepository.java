package com.beachape.aminam.domain.orgs.repositories;

import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.beachape.aminam.domain.orgs.models.Organisation;
import java.util.List;
import org.jspecify.annotations.Nullable;

public interface OrganisationRepository {

  Organisation create(Organisation organisation);

  @Nullable Organisation findById(OrgId id);

  /// The organisations the principal is a member of, oldest by org creation time first.
  List<Organisation> listByMember(UserId principalId);
}
