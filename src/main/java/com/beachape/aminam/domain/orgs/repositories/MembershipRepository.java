package com.beachape.aminam.domain.orgs.repositories;

import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.errors.DomainException;
import com.beachape.aminam.domain.orgs.models.Membership;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import java.util.List;
import org.jspecify.annotations.Nullable;

public interface MembershipRepository {

  Membership create(Membership membership) throws DuplicateMembershipException;

  @Nullable Membership find(UserId principalId, OrgId orgId);

  /// Resolves a membership's owning org by id (the authz fact lookup); null if none.
  @Nullable Membership findById(MembershipId id);

  /// The org's roster.
  List<Membership> listByOrg(OrgId orgId);

  void delete(Membership membership);

  final class DuplicateMembershipException extends DomainException {
    public DuplicateMembershipException(UserId principalId, OrgId orgId, Throwable cause) {
      super(
          "principal " + principalId.value() + " is already a member of org " + orgId.value(),
          cause);
    }
  }
}
