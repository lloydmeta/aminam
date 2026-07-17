package com.beachape.aminam.domain.authc.services;

import com.beachape.aminam.domain.authc.models.AccessToken;
import com.beachape.aminam.domain.authc.models.AuthenticatedUser;
import com.beachape.aminam.domain.errors.DomainException;

public interface TokenService {

  AccessToken issue(AuthenticatedUser principal);

  AuthenticatedUser authenticate(AccessToken token)
      throws InvalidTokenException, ExpiredTokenException, RevokedTokenException;

  void revoke(AccessToken token);

  final class InvalidTokenException extends DomainException {

    public InvalidTokenException(String message) {
      super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  final class ExpiredTokenException extends DomainException {
    public ExpiredTokenException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  final class RevokedTokenException extends DomainException {
    public RevokedTokenException(String message) {
      super(message);
    }
  }
}
