package com.beachape.aminam.app.routes.v1.policies.models;

import com.beachape.aminam.domain.authz.models.PolicyDocument;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PolicyRequest(
    @NotBlank @Size(max = 255) String name,
    @NotEmpty @Size(max = 64) List<@Valid PolicyStatement> statements) {

  public PolicyDocument toDomain() {
    return new PolicyDocument(statements.stream().map(PolicyStatement::toDomain).toList());
  }
}
