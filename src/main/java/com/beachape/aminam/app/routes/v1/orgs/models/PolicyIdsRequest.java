package com.beachape.aminam.app.routes.v1.orgs.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PolicyIdsRequest(
    @NotNull @Size(max = 50) List<@NotBlank @Size(max = 128) @Pattern(regexp = POLICY_ID_PATTERN) String> policyIds) {

  public static final String POLICY_ID_PATTERN =
      "^(system:[a-z]+"
          + "|[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$";
}
