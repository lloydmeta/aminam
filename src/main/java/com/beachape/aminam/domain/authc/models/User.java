package com.beachape.aminam.domain.authc.models;

import java.time.Instant;

public record User(UserId id, String username, PasswordHash passwordHash, Instant createdAt) {
  public static final String USERNAME_PATTERN = "^[A-Za-z0-9._@+-]+$";
}
