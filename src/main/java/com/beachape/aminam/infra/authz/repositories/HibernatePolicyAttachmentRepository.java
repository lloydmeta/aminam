package com.beachape.aminam.infra.authz.repositories;

import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toUnmodifiableSet;

import com.beachape.aminam.domain.authz.models.AttachmentPoint;
import com.beachape.aminam.domain.authz.models.PolicyAttachment;
import com.beachape.aminam.domain.authz.models.PolicyId;
import com.beachape.aminam.domain.authz.repositories.PolicyAttachmentRepository;
import com.beachape.aminam.infra.authz.repositories.entities.PolicyAttachmentEntity;
import com.beachape.aminam.infra.authz.repositories.panache.PanachePolicyAttachmentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@ApplicationScoped
class HibernatePolicyAttachmentRepository implements PolicyAttachmentRepository {

  @Inject PanachePolicyAttachmentRepository panache;

  @Override
  @Transactional
  public void attach(PolicyAttachment attachment) {
    panache.upsert(
        randomUUID(),
        attachment.point().type().name(),
        attachment.point().id(),
        attachment.policyId().asText());
  }

  @Override
  @Transactional
  public List<PolicyAttachment> findByPoint(AttachmentPoint point) {
    return findByPoints(List.of(point));
  }

  @Override
  @Transactional
  public List<PolicyAttachment> findByPoints(Collection<AttachmentPoint> points) {
    if (points.isEmpty()) {
      return List.of();
    }
    var idsByType =
        points.stream()
            .collect(
                groupingBy(
                    point -> point.type().name(),
                    mapping(AttachmentPoint::id, toUnmodifiableSet())));
    var result = new ArrayList<PolicyAttachment>();
    idsByType.forEach(
        (type, ids) ->
            panache.findByPointIds(type, ids).stream()
                .map(PolicyAttachmentEntity.Mapper::toDomain)
                .forEach(result::add));
    return result;
  }

  @Override
  @Transactional
  public void detach(PolicyAttachment attachment) {
    panache.detach(
        attachment.point().type().name(), attachment.point().id(), attachment.policyId().asText());
  }

  @Override
  @Transactional
  public void deleteByPoint(AttachmentPoint point) {
    panache.deleteByPoint(point.type().name(), point.id());
  }

  @Override
  @Transactional
  public void deleteByPolicyId(PolicyId policyId) {
    panache.deleteByPolicyId(policyId.asText());
  }
}
