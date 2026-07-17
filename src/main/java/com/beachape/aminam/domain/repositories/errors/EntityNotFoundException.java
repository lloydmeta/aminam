package com.beachape.aminam.domain.repositories.errors;

import com.beachape.aminam.domain.errors.DomainException;

public final class EntityNotFoundException extends DomainException {
  public EntityNotFoundException() {
    super("entity not found");
  }
}
