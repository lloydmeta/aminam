package com.beachape.aminam.integration.infra.authz.repositories;

import static com.beachape.aminam.domain.authz.models.AttachmentType.MEMBERSHIP;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.domain.authz.models.AttachmentPoint;
import com.beachape.aminam.domain.authz.models.PolicyAttachment;
import com.beachape.aminam.domain.authz.models.PolicyId.CustomPolicyId;
import com.beachape.aminam.domain.authz.repositories.PolicyAttachmentRepository;
import com.beachape.aminam.domain.authz.services.SystemPolicies;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
final class HibernatePolicyAttachmentRepositoryTest {

  @Inject PolicyAttachmentRepository attachments;

  @Test
  void attachThenFindByPointReturnsIt() {
    var point = membershipPoint();
    attachments.attach(new PolicyAttachment(point, SystemPolicies.MANAGER));

    assertThat(attachments.findByPoint(point))
        .containsExactly(new PolicyAttachment(point, SystemPolicies.MANAGER));
  }

  @Test
  void attachIsIdempotent() {
    var point = membershipPoint();
    attachments.attach(new PolicyAttachment(point, SystemPolicies.MANAGER));
    attachments.attach(new PolicyAttachment(point, SystemPolicies.MANAGER));

    assertThat(attachments.findByPoint(point)).hasSize(1);
  }

  @Test
  void multiplePoliciesPerPointAreUnioned() {
    var point = membershipPoint();
    attachments.attach(new PolicyAttachment(point, SystemPolicies.MANAGER));
    attachments.attach(new PolicyAttachment(point, SystemPolicies.VIEWER));

    assertThat(attachments.findByPoint(point))
        .containsExactlyInAnyOrder(
            new PolicyAttachment(point, SystemPolicies.MANAGER),
            new PolicyAttachment(point, SystemPolicies.VIEWER));
  }

  @Test
  void findByPointIsolatesDistinctPoints() {
    var point = membershipPoint();
    attachments.attach(new PolicyAttachment(point, SystemPolicies.MANAGER));

    assertThat(attachments.findByPoint(membershipPoint())).isEmpty();
  }

  @Test
  void findByPointsCollectsAttachmentsAcrossEveryPoint() {
    var first = membershipPoint();
    var second = membershipPoint();
    attachments.attach(new PolicyAttachment(first, SystemPolicies.MANAGER));
    attachments.attach(new PolicyAttachment(first, SystemPolicies.VIEWER));
    attachments.attach(new PolicyAttachment(second, SystemPolicies.ADMIN));
    attachments.attach(new PolicyAttachment(membershipPoint(), SystemPolicies.VIEWER)); // unrelated

    assertThat(attachments.findByPoints(List.of(first, second)))
        .containsExactlyInAnyOrder(
            new PolicyAttachment(first, SystemPolicies.MANAGER),
            new PolicyAttachment(first, SystemPolicies.VIEWER),
            new PolicyAttachment(second, SystemPolicies.ADMIN));
  }

  @Test
  void findByPointsWithNoPointsReturnsEmpty() {
    attachments.attach(new PolicyAttachment(membershipPoint(), SystemPolicies.MANAGER));

    assertThat(attachments.findByPoints(List.of())).isEmpty();
  }

  @Test
  void detachRemovesOnePolicyAndLeavesSiblings() {
    var point = membershipPoint();
    attachments.attach(new PolicyAttachment(point, SystemPolicies.MANAGER));
    attachments.attach(new PolicyAttachment(point, SystemPolicies.VIEWER));

    attachments.detach(new PolicyAttachment(point, SystemPolicies.VIEWER));

    assertThat(attachments.findByPoint(point))
        .containsExactly(new PolicyAttachment(point, SystemPolicies.MANAGER));
  }

  @Test
  void detachOfAnAbsentPolicyIsANoOp() {
    var point = membershipPoint();
    attachments.attach(new PolicyAttachment(point, SystemPolicies.MANAGER));

    attachments.detach(new PolicyAttachment(point, SystemPolicies.VIEWER));

    assertThat(attachments.findByPoint(point))
        .containsExactly(new PolicyAttachment(point, SystemPolicies.MANAGER));
  }

  @Test
  void deleteByPointRemovesEveryAttachmentAtThePoint() {
    var point = membershipPoint();
    attachments.attach(new PolicyAttachment(point, SystemPolicies.MANAGER));
    attachments.attach(new PolicyAttachment(point, SystemPolicies.VIEWER));

    attachments.deleteByPoint(point);

    assertThat(attachments.findByPoint(point)).isEmpty();
  }

  @Test
  void deleteByPointLeavesOtherPointsIntact() {
    var keep = membershipPoint();
    attachments.attach(new PolicyAttachment(keep, SystemPolicies.MANAGER));
    var drop = membershipPoint();
    attachments.attach(new PolicyAttachment(drop, SystemPolicies.VIEWER));

    attachments.deleteByPoint(drop);

    assertThat(attachments.findByPoint(keep))
        .containsExactly(new PolicyAttachment(keep, SystemPolicies.MANAGER));
  }

  @Test
  void deleteByPolicyIdRemovesThatPolicyEverywhereAndLeavesOthers() {
    var custom = new CustomPolicyId(randomUUID());
    var first = membershipPoint();
    var second = membershipPoint();
    attachments.attach(new PolicyAttachment(first, custom));
    attachments.attach(new PolicyAttachment(second, custom));
    attachments.attach(new PolicyAttachment(first, SystemPolicies.MANAGER)); // unrelated, kept

    attachments.deleteByPolicyId(custom);

    assertThat(attachments.findByPoint(first))
        .containsExactly(new PolicyAttachment(first, SystemPolicies.MANAGER));
    assertThat(attachments.findByPoint(second)).isEmpty();
  }

  private static AttachmentPoint membershipPoint() {
    return new AttachmentPoint(MEMBERSHIP, randomUUID());
  }
}
