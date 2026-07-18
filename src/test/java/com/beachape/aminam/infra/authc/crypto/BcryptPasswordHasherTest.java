package com.beachape.aminam.infra.authc.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import com.password4j.Password;
import org.junit.jupiter.api.Test;

final class BcryptPasswordHasherTest {

  private final BcryptPasswordHasher hasher = new BcryptPasswordHasher();

  @Test
  void hashIsNotPlaintextAndVerifiesAgainstTheOriginal() {
    var hash = hasher.hash("passw0rd");

    assertThat(hash.value()).isNotEqualTo("passw0rd");
    assertThat(Password.check("passw0rd", hash.value()).withBcrypt()).isTrue();
    assertThat(Password.check("wrong passw0rd", hash.value()).withBcrypt()).isFalse();
  }

  @Test
  void hashingTheSamePasswordTwiceProducesDifferentSalts() {
    assertThat(hasher.hash("same passw0rd")).isNotEqualTo(hasher.hash("same passw0rd"));
  }

  @Test
  void verifyAcceptsTheOriginalAndRejectsAnyOther() {
    var hash = hasher.hash("passw0rd");

    assertThat(hasher.verify("passw0rd", hash)).isTrue();
    assertThat(hasher.verify("wrong passw0rd", hash)).isFalse();
  }
}
