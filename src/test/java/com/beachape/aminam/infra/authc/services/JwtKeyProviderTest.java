package com.beachape.aminam.infra.authc.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JwtKeyProviderTest {

  @Test
  void generatesAnEphemeralRsaKeypairWhenUnconfigured() {
    var provider = new JwtKeyProvider(Optional.<String>empty(), Optional.<String>empty());

    assertThat(provider.privateKey().getAlgorithm()).isEqualTo("RSA");
    assertThat(provider.publicKey().getAlgorithm()).isEqualTo("RSA");
  }

  @Test
  void loadsConfiguredPemKeys(@TempDir Path dir) throws Exception {
    var pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
    var privatePath = dir.resolve("private.pem");
    var publicPath = dir.resolve("public.pem");
    Files.writeString(privatePath, pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
    Files.writeString(publicPath, pem("PUBLIC KEY", pair.getPublic().getEncoded()));

    var provider =
        new JwtKeyProvider(Optional.of(privatePath.toString()), Optional.of(publicPath.toString()));

    assertThat(provider.privateKey().getEncoded()).isEqualTo(pair.getPrivate().getEncoded());
    assertThat(provider.publicKey().getEncoded()).isEqualTo(pair.getPublic().getEncoded());
  }

  @Test
  void rejectsConfiguringOnlyOneLocation() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new JwtKeyProvider(Optional.of("private.pem"), Optional.<String>empty()));
  }

  @Test
  void failsWhenAConfiguredKeyFileIsMissing(@TempDir Path dir) {
    var missing = dir.resolve("absent.pem").toString();

    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> new JwtKeyProvider(Optional.of(missing), Optional.of(missing)));
  }

  private static String pem(String type, byte[] der) {
    var body = Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der);
    return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
  }
}
