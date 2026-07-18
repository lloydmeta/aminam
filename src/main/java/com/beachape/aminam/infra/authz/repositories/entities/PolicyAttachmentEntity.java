package com.beachape.aminam.infra.authz.repositories.entities;

import com.beachape.aminam.domain.authz.models.AttachmentPoint;
import com.beachape.aminam.domain.authz.models.AttachmentType;
import com.beachape.aminam.domain.authz.models.PolicyAttachment;
import com.beachape.aminam.domain.authz.models.PolicyId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "policy_attachments")
public class PolicyAttachmentEntity {

  @Id
  @Column(name = "id", nullable = false)
  UUID id;

  @Column(name = "attached_to_type", nullable = false, length = 16)
  String attachedToType;

  @Column(name = "attached_to_id", nullable = false)
  UUID attachedToId;

  @Column(name = "policy_id", nullable = false, length = 128)
  String policyId;

  public static final class Mapper {

    private Mapper() {}

    public static PolicyAttachment toDomain(PolicyAttachmentEntity entity) {
      return new PolicyAttachment(
          new AttachmentPoint(AttachmentType.valueOf(entity.attachedToType), entity.attachedToId),
          PolicyId.unsafeFromStoredText(entity.policyId));
    }
  }
}
