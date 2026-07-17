package com.beachape.aminam.infra.authc.crypto;

import com.beachape.aminam.domain.authc.crypto.PasswordHasher;
import com.beachape.aminam.domain.authc.models.PasswordHash;
import com.password4j.Password;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
class BcryptPasswordHasher implements PasswordHasher {

  @Override
  public PasswordHash hash(String plaintext) {
    return new PasswordHash(Password.hash(plaintext).withBcrypt().getResult());
  }

  @Override
  public boolean verify(String plaintext, PasswordHash hash) {
    return Password.check(plaintext, hash.value()).withBcrypt();
  }
}
