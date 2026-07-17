package com.beachape.aminam.domain.errors;

public final class NotAuthorisedException extends DomainException {
  public NotAuthorisedException() {
    super("not authorised");
  }
}
