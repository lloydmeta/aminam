package com.beachape.aminam.domain.orgs.models;

import java.util.UUID;

public record OrgId(UUID value) {
  @Override
  public String toString() {
    return value.toString();
  }
}
