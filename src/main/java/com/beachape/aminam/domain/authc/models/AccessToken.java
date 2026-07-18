package com.beachape.aminam.domain.authc.models;

public record AccessToken(String value) {

  @Override
  public String toString() {
    return "AccessToken[REDACTED]"; // prevent leaks
  }
}
