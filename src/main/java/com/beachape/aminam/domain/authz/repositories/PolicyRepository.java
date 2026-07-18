package com.beachape.aminam.domain.authz.repositories;

import com.beachape.aminam.domain.authz.models.Policy;
import com.beachape.aminam.domain.authz.models.PolicyId.CustomPolicyId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.beachape.aminam.domain.repositories.errors.EntityNotFoundException;
import java.util.List;
import org.jspecify.annotations.Nullable;

public interface PolicyRepository {

  Policy create(Policy policy);

  @Nullable Policy findById(CustomPolicyId id);

  List<Policy> listByOrg(OrgId orgId);

  Policy update(Policy policy) throws EntityNotFoundException;

  void delete(Policy policy);
}
