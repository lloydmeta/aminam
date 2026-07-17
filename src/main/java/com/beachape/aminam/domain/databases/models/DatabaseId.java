package com.beachape.aminam.domain.databases.models;

import java.util.UUID;

public record DatabaseId(UUID value) {
  @Override
  public String toString() {
    return value.toString();
  }
}
