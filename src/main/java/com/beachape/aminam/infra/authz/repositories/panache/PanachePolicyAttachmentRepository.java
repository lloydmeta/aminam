package com.beachape.aminam.infra.authz.repositories.panache;

import com.beachape.aminam.infra.authz.repositories.entities.PolicyAttachmentEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PanachePolicyAttachmentRepository
    implements PanacheRepositoryBase<PolicyAttachmentEntity, UUID> {

  /// Idempotent insert: a duplicate (same point + policy) is silently ignored, so attach never
  /// throws on a constraint violation (which would mark the surrounding transaction rollback-only).
  public void upsert(UUID id, String attachedToType, UUID attachedToId, String policyId) {
    getEntityManager()
        .createNativeQuery(
            "INSERT INTO policy_attachments (id, attached_to_type, attached_to_id, policy_id) "
                + "VALUES (?1, ?2, ?3, ?4) "
                + "ON CONFLICT (attached_to_type, attached_to_id, policy_id) DO NOTHING")
        .setParameter(1, id)
        .setParameter(2, attachedToType)
        .setParameter(3, attachedToId)
        .setParameter(4, policyId)
        .executeUpdate();
  }

  public List<PolicyAttachmentEntity> findByPointIds(String attachedToType, Collection<UUID> ids) {
    return list("attachedToType = ?1 and attachedToId in ?2", attachedToType, ids);
  }

  public long detach(String attachedToType, UUID attachedToId, String policyId) {
    return delete(
        "attachedToType = ?1 and attachedToId = ?2 and policyId = ?3",
        attachedToType,
        attachedToId,
        policyId);
  }

  public long deleteByPoint(String attachedToType, UUID attachedToId) {
    return delete("attachedToType = ?1 and attachedToId = ?2", attachedToType, attachedToId);
  }

  public long deleteByPolicyId(String policyId) {
    return delete("policyId = ?1", policyId);
  }
}
