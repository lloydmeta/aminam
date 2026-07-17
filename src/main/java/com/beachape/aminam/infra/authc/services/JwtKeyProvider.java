package com.beachape.aminam.infra.authc.services;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@Startup
@ApplicationScoped
public class JwtKeyProvider {

  private static final Logger LOG = Logger.getLogger(JwtKeyProvider.class);
  private static final int KEY_SIZE = 2048;

  private final PrivateKey privateKey;
  private final PublicKey publicKey;

  @Inject
  JwtKeyProvider(
      @ConfigProperty(name = "aminam.jwt.private-key-location") Optional<String> privateKeyLocation,
      @ConfigProperty(name = "aminam.jwt.public-key-location") Optional<String> publicKeyLocation) {
    if (privateKeyLocation.isPresent() || publicKeyLocation.isPresent()) {
      if (privateKeyLocation.isEmpty() || publicKeyLocation.isEmpty()) {
        throw new IllegalArgumentException(
            "configure both aminam.jwt.private-key-location and"
                + " aminam.jwt.public-key-location, or neither");
      }
      this.privateKey = readPrivateKey(privateKeyLocation.get());
      this.publicKey = readPublicKey(publicKeyLocation.get());
      LOG.infov("Loaded JWT signing keys from {0}", privateKeyLocation.get());
    } else {
      var pair = generateKeyPair();
      this.privateKey = pair.getPrivate();
      this.publicKey = pair.getPublic();
      LOG.warn(
          "No JWT key locations configured; generated an ephemeral RSA keypair. Tokens will not"
              + " survive a restart. Generate and mount keys for production (make keys).");
    }
  }

  public PrivateKey privateKey() {
    return privateKey;
  }

  public PublicKey publicKey() {
    return publicKey;
  }

  private static KeyPair generateKeyPair() {
    try {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(KEY_SIZE);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("RSA key generation unavailable", e);
    }
  }

  private static PrivateKey readPrivateKey(String location) {
    try {
      var der = pemBody(Files.readString(Path.of(location)));
      return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new IllegalStateException("could not read JWT private key from " + location, e);
    }
  }

  private static PublicKey readPublicKey(String location) {
    try {
      var der = pemBody(Files.readString(Path.of(location)));
      return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new IllegalStateException("could not read JWT public key from " + location, e);
    }
  }

  private static byte[] pemBody(String pem) {
    var base64 = pem.replaceAll("-----BEGIN [^-]+-----", "").replaceAll("-----END [^-]+-----", "");
    return Base64.getMimeDecoder().decode(base64);
  }
}
