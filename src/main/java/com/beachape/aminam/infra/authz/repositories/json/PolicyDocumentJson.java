package com.beachape.aminam.infra.authz.repositories.json;

import static java.util.stream.Collectors.toSet;

import com.beachape.aminam.domain.authz.models.PolicyDocument;
import com.beachape.aminam.domain.authz.models.Statement;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// The JSONB representation of a PolicyDocument: own mirror types (structured, bare UUIDs) so the
/// stored shape is decoupled from the domain records; a domain rename does not change the column.
/// The nested Mapper is the only translation to/from the domain.
public record PolicyDocumentJson(List<StatementJson> statements) {

  public record StatementJson(
      EffectJson effect,
      Set<UUID> memberships,
      List<ActionJson> actions,
      List<ResourcePatternJson> resources,
      @Nullable String condition) {}

  public static final class Mapper {

    private Mapper() {}

    public static PolicyDocumentJson fromDomain(PolicyDocument document) {
      return new PolicyDocumentJson(
          document.statements().stream().map(Mapper::fromDomain).toList());
    }

    private static StatementJson fromDomain(Statement statement) {
      return new StatementJson(
          EffectJson.from(statement.effect()),
          statement.memberships().stream().map(MembershipId::value).collect(toSet()),
          statement.actions().stream().map(ActionJson::from).toList(),
          statement.resources().stream().map(ResourcePatternJson::from).toList(),
          statement.condition());
    }

    public static PolicyDocument toDomain(PolicyDocumentJson json) {
      return new PolicyDocument(json.statements().stream().map(Mapper::toDomain).toList());
    }

    private static Statement toDomain(StatementJson json) {
      return new Statement(
          json.effect().toDomain(),
          json.memberships().stream().map(MembershipId::new).collect(toSet()),
          json.actions().stream().map(ActionJson::toDomain).collect(toSet()),
          json.resources().stream().map(ResourcePatternJson::toDomain).collect(toSet()),
          json.condition());
    }
  }
}
