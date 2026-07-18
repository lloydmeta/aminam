package com.beachape.aminam.app.routes.v1.orgs.models;

import static com.beachape.aminam.app.routes.v1.orgs.models.PolicyIdsRequest.POLICY_ID_PATTERN;
import static com.beachape.aminam.domain.authc.models.User.USERNAME_PATTERN;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AddMemberRequest(
    @NotBlank @Size(min = 3, max = 255) @Pattern(regexp = USERNAME_PATTERN) String username,
    @NotEmpty @Size(max = 50) List<@NotBlank @Size(max = 128) @Pattern(regexp = POLICY_ID_PATTERN) String> policyIds) {}
