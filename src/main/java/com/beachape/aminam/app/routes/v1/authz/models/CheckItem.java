package com.beachape.aminam.app.routes.v1.authz.models;

import com.beachape.aminam.domain.authz.models.Action;
import com.beachape.aminam.domain.authz.models.ResourceRef;
import com.beachape.aminam.domain.authz.services.AuthorisationService.Check;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
  @JsonSubTypes.Type(value = CheckItem.Existing.class, name = "EXISTING"),
  @JsonSubTypes.Type(value = CheckItem.ToCreate.class, name = "TO_CREATE")
})
@Schema(
    oneOf = {CheckItem.Existing.class, CheckItem.ToCreate.class},
    discriminatorProperty = "kind",
    discriminatorMapping = {
      @DiscriminatorMapping(value = "EXISTING", schema = CheckItem.Existing.class),
      @DiscriminatorMapping(value = "TO_CREATE", schema = CheckItem.ToCreate.class)
    })
public sealed interface CheckItem {

  Check toDomain();

  @Schema(name = "ExistingCheck")
  record Existing(@NotNull AuthzResourceType type, @NotNull AuthzVerb verb, @NotNull UUID id)
      implements CheckItem {

    @Override
    public Check toDomain() {
      var resourceType = type.toDomain();
      return new Check(
          new Action(resourceType, verb.toDomain()), new ResourceRef.Existing(resourceType, id));
    }
  }

  @Schema(name = "ToCreateCheck")
  record ToCreate(
      @NotNull AuthzResourceType type, @NotNull AuthzVerb verb, @NotNull UUID owningOrgId)
      implements CheckItem {

    @Override
    public Check toDomain() {
      var resourceType = type.toDomain();
      return new Check(
          new Action(resourceType, verb.toDomain()),
          new ResourceRef.ToCreate(resourceType, new OrgId(owningOrgId)));
    }
  }
}
