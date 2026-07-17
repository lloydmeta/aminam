package com.beachape.aminam.domain.errors;

import java.util.Locale;

public final class NotFoundException extends DomainException {

  public enum Type {
    DATABASE,
    MEMBER,
    ORGANISATION,
    POLICY,
    USER;

    final String displayName;

    Type() {
      this.displayName = name().toLowerCase(Locale.ROOT);
    }
  }

  private final Type type;

  public NotFoundException(Type type) {
    super(type.displayName + " not found");
    this.type = type;
  }

  public NotFoundException(Type type, Throwable cause) {
    super(type.displayName + " not found", cause);
    this.type = type;
  }

  public Type type() {
    return type;
  }

  public String displayMessage() {
    return type.displayName + " not found";
  }
}
