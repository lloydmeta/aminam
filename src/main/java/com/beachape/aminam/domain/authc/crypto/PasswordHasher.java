package com.beachape.aminam.domain.authc.crypto;

import com.beachape.aminam.domain.authc.models.PasswordHash;

public interface PasswordHasher {

  PasswordHash hash(String plaintext);

  boolean verify(String plaintext, PasswordHash hash);
}
