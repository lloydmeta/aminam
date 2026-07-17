package com.beachape.aminam.domain.authz.repositories;

import com.beachape.aminam.domain.authz.models.AttachmentPoint;
import com.beachape.aminam.domain.authz.models.PolicyAttachment;
import com.beachape.aminam.domain.authz.models.PolicyId;
import java.util.Collection;
import java.util.List;

public interface PolicyAttachmentRepository {

  /// Attaches a policy to a point. Idempotent: attaching the same policy twice is a no-op.
  void attach(PolicyAttachment attachment);

  List<PolicyAttachment> findByPoint(AttachmentPoint point);

  /// Every attachment hung on any of the given points (batch gather); empty for no points.
  List<PolicyAttachment> findByPoints(Collection<AttachmentPoint> points);

  /// Removes a single policy from a point; a no-op if it was not attached.
  void detach(PolicyAttachment attachment);

  /// Removes every policy attached to a point (cascades a membership's identity policies).
  void deleteByPoint(AttachmentPoint point);

  /// Removes every attachment of a given policy across all points (cascades a deleted custom
  /// policy's grants); a no-op if it was attached nowhere.
  void deleteByPolicyId(PolicyId policyId);
}
