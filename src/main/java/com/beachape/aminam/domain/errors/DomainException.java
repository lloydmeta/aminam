package com.beachape.aminam.domain.errors;

/// Base for domain checked exceptions. These model expected, handled outcomes (control flow), not
/// bugs, so they carry no stack trace.
public abstract class DomainException extends Exception {

  protected DomainException(String message) {
    super(message, null, /* enableSuppression= */ false, /* writableStackTrace= */ false);
  }

  protected DomainException(String message, Throwable cause) {
    super(message, cause, /* enableSuppression= */ false, /* writableStackTrace= */ false);
  }
}
