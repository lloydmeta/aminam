package com.beachape.aminam.app.routes.v1.policies.models;

import static java.util.stream.Collectors.toSet;

import com.beachape.aminam.domain.authz.models.ConditionAttributes;
import com.beachape.aminam.domain.authz.models.Statement;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

public record PolicyStatement(
    @NotNull PolicyEffect effect,
    @Size(max = 128) @Nullable Set<@NotNull UUID> memberships,
    @NotEmpty @Size(max = 64) List<@NotNull @Valid PolicyAction> actions,
    @NotEmpty @Size(max = 128) List<@NotNull @Valid PolicyResourcePattern> resources,
    @Size(max = 2048) @Schema(description = ConditionAttributes.CONDITION_DESCRIPTION)
        @Nullable String condition) {

  Statement toDomain() {
    var members =
        memberships == null
            ? Set.<MembershipId>of()
            : memberships.stream().map(MembershipId::new).collect(toSet());
    return new Statement(
        effect.toDomain(),
        members,
        actions.stream().map(PolicyAction::toDomain).collect(toSet()),
        resources.stream().map(PolicyResourcePattern::toDomain).collect(toSet()),
        condition);
  }

  static PolicyStatement from(Statement statement) {
    return new PolicyStatement(
        PolicyEffect.from(statement.effect()),
        statement.memberships().stream().map(MembershipId::value).collect(toSet()),
        statement.actions().stream().map(PolicyAction::from).toList(),
        statement.resources().stream().map(PolicyResourcePattern::from).toList(),
        statement.condition());
  }
}
