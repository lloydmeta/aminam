package com.beachape.aminam.infra.orgs.repositories;

import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.errors.DomainException;
import com.beachape.aminam.domain.orgs.models.Membership;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.beachape.aminam.domain.orgs.repositories.MembershipRepository;
import com.beachape.aminam.infra.orgs.repositories.entities.MembershipEntity;
import com.beachape.aminam.infra.orgs.repositories.panache.PanacheMembershipRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

@ApplicationScoped
class HibernateMembershipRepository implements MembershipRepository {

  private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

  @Inject PanacheMembershipRepository panache;

  @Override
  @Transactional(rollbackOn = DomainException.class)
  public Membership create(Membership membership) throws DuplicateMembershipException {
    var entity = MembershipEntity.Mapper.toEntity(membership);
    try {
      panache.persist(entity);
      panache.flush(); // surface the (principal, org) violation here, not at commit
      return MembershipEntity.Mapper.toDomain(entity);
    } catch (PersistenceException e) {
      if (isUniqueViolation(e)) {
        throw new DuplicateMembershipException(membership.userId(), membership.orgId(), e);
      }
      throw e;
    }
  }

  @Override
  @Transactional
  public @Nullable Membership find(UserId principalId, OrgId orgId) {
    return panache
        .find(principalId.value(), orgId.value())
        .map(MembershipEntity.Mapper::toDomain)
        .orElse(null);
  }

  @Override
  @Transactional
  public @Nullable Membership findById(MembershipId id) {
    return panache.findByIdOptional(id.value()).map(MembershipEntity.Mapper::toDomain).orElse(null);
  }

  @Override
  @Transactional
  public List<Membership> listByOrg(OrgId orgId) {
    return panache.listByOrg(orgId.value()).stream()
        .map(MembershipEntity.Mapper::toDomain)
        .toList();
  }

  @Override
  @Transactional
  public void delete(Membership membership) {
    panache.deleteById(membership.id().value());
  }

  private static boolean isUniqueViolation(Throwable t) {
    for (Throwable cause = t; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql
          && Objects.equals(sql.getSQLState(), UNIQUE_VIOLATION_SQL_STATE)) {
        return true;
      }
    }
    return false;
  }
}
