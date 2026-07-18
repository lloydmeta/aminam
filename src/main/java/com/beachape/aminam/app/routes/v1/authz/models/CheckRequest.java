package com.beachape.aminam.app.routes.v1.authz.models;

import com.beachape.aminam.domain.authz.services.AuthorisationService.Check;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CheckRequest(@NotEmpty @Size(max = 100) List<@NotNull @Valid CheckItem> checks) {

  public List<Check> toDomain() {
    return checks.stream().map(CheckItem::toDomain).toList();
  }
}
