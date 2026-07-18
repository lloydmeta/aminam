package com.beachape.aminam.infra.orgs.repositories;

import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.beachape.aminam.domain.orgs.models.Organisation;
import com.beachape.aminam.domain.orgs.repositories.OrganisationRepository;
import com.beachape.aminam.infra.orgs.repositories.entities.OrgEntity;
import com.beachape.aminam.infra.orgs.repositories.panache.PanacheOrgRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import org.jspecify.annotations.Nullable;

@ApplicationScoped
class HibernateOrganisationRepository implements OrganisationRepository {

  @Inject PanacheOrgRepository panache;

  @Override
  @Transactional
  public Organisation create(Organisation organisation) {
    var entity = OrgEntity.Mapper.toEntity(organisation);
    panache.persist(entity);
    return OrgEntity.Mapper.toDomain(entity);
  }

  @Override
  @Transactional
  public @Nullable Organisation findById(OrgId id) {
    var entity = panache.findById(id.value());
    return entity == null ? null : OrgEntity.Mapper.toDomain(entity);
  }

  @Override
  @Transactional
  public List<Organisation> listByMember(UserId principalId) {
    return panache.listByMember(principalId.value()).stream()
        .map(OrgEntity.Mapper::toDomain)
        .toList();
  }
}
