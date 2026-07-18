package com.beachape.aminam.infra.authz.repositories;

import com.beachape.aminam.domain.authz.models.Policy;
import com.beachape.aminam.domain.authz.models.PolicyId.CustomPolicyId;
import com.beachape.aminam.domain.authz.repositories.PolicyRepository;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.beachape.aminam.domain.repositories.errors.EntityNotFoundException;
import com.beachape.aminam.infra.authz.repositories.entities.PolicyEntity;
import com.beachape.aminam.infra.authz.repositories.panache.PanachePolicyRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import org.jspecify.annotations.Nullable;

@ApplicationScoped
class HibernatePolicyRepository implements PolicyRepository {

  @Inject PanachePolicyRepository panache;

  @Override
  @Transactional
  public Policy create(Policy policy) {
    var entity = PolicyEntity.Mapper.toEntity(policy);
    panache.persist(entity);
    return PolicyEntity.Mapper.toDomain(entity);
  }

  @Override
  @Transactional
  public @Nullable Policy findById(CustomPolicyId id) {
    var entity = panache.findById(id.value());
    return entity == null ? null : PolicyEntity.Mapper.toDomain(entity);
  }

  @Override
  @Transactional
  public List<Policy> listByOrg(OrgId orgId) {
    return panache.listByOrg(orgId.value()).stream().map(PolicyEntity.Mapper::toDomain).toList();
  }

  @Override
  @Transactional
  public Policy update(Policy policy) throws EntityNotFoundException {
    var entity = panache.findById(policy.id().value());
    if (entity == null) {
      throw new EntityNotFoundException();
    }
    // Mutate the managed entity; dirty checking flushes the UPDATE.
    PolicyEntity.Mapper.applyChanges(entity, policy);
    return PolicyEntity.Mapper.toDomain(entity);
  }

  @Override
  @Transactional
  public void delete(Policy policy) {
    panache.deleteById(policy.id().value());
  }
}
