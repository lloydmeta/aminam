package com.beachape.aminam.infra.authz.repositories.entities;

import com.beachape.aminam.domain.authz.models.Policy;
import com.beachape.aminam.domain.authz.models.PolicyDocument;
import com.beachape.aminam.domain.authz.models.PolicyId.CustomPolicyId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.beachape.aminam.infra.authz.repositories.json.PolicyDocumentJson;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "policies")
public class PolicyEntity {

  @Id
  @Column(name = "id", nullable = false)
  UUID id;

  @Column(name = "org_id", nullable = false)
  UUID orgId;

  @Column(name = "name", nullable = false)
  String name;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "document", nullable = false)
  String document;

  @Column(name = "created_at", nullable = false)
  Instant createdAt;

  public static final class Mapper {

    private static final ObjectMapper JSON = new ObjectMapper();

    private Mapper() {}

    public static Policy toDomain(PolicyEntity entity) {
      return new Policy(
          new CustomPolicyId(entity.id),
          new OrgId(entity.orgId),
          entity.name,
          readDocument(entity.document),
          entity.createdAt);
    }

    public static PolicyEntity toEntity(Policy policy) {
      var entity = new PolicyEntity();
      entity.id = policy.id().value();
      entity.orgId = policy.orgId().value();
      entity.name = policy.name();
      entity.document = writeDocument(policy.document());
      entity.createdAt = policy.createdAt();
      return entity;
    }

    public static void applyChanges(PolicyEntity entity, Policy policy) {
      // id, orgId and createdAt are immutable; name and document are the editable fields.
      entity.name = policy.name();
      entity.document = writeDocument(policy.document());
    }

    private static String writeDocument(PolicyDocument document) {
      try {
        return JSON.writeValueAsString(PolicyDocumentJson.Mapper.fromDomain(document));
      } catch (JsonProcessingException e) {
        throw new IllegalStateException("serialising policy document", e);
      }
    }

    private static PolicyDocument readDocument(String json) {
      try {
        return PolicyDocumentJson.Mapper.toDomain(JSON.readValue(json, PolicyDocumentJson.class));
      } catch (JsonProcessingException e) {
        throw new IllegalStateException("parsing policy document", e);
      }
    }
  }
}
