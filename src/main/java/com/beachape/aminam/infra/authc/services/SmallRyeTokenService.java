package com.beachape.aminam.infra.authc.services;

import static java.util.UUID.fromString;
import static java.util.UUID.randomUUID;

import com.beachape.aminam.domain.authc.models.AccessToken;
import com.beachape.aminam.domain.authc.models.AuthenticatedUser;
import com.beachape.aminam.domain.authc.models.TokenId;
import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.authc.services.TokenRevocationService;
import com.beachape.aminam.domain.authc.services.TokenService;
import com.beachape.aminam.domain.errors.DomainException;
import com.beachape.aminam.domain.orgs.models.Membership;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.auth.principal.DefaultJWTParser;
import io.smallrye.jwt.auth.principal.JWTAuthContextInfo;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jspecify.annotations.Nullable;

@ApplicationScoped
class SmallRyeTokenService implements TokenService {

  private static final String USERNAME_CLAIM = "username";
  private static final String ORG_CLAIM = "org";
  private static final String MEMBERSHIP_CLAIM = "mid";
  private static final SignatureAlgorithm SIGNATURE_ALGORITHM = SignatureAlgorithm.RS256;

  private final JwtKeyProvider keys;
  private final TokenRevocationService revokeService;
  private final Clock clock;
  private final String issuer;
  private final String audience;
  private final long lifespanSeconds;
  private final JWTParser parser;

  @Inject
  SmallRyeTokenService(
      JwtKeyProvider keys,
      TokenRevocationService revokeService,
      Clock clock,
      @ConfigProperty(name = "aminam.jwt.issuer") String issuer,
      @ConfigProperty(name = "aminam.jwt.audience") String audience,
      @ConfigProperty(name = "aminam.jwt.lifespan-seconds") long lifespanSeconds,
      @ConfigProperty(name = "aminam.jwt.clock-skew-seconds", defaultValue = "30")
          int clockSkewSeconds) {
    this.keys = keys;
    this.revokeService = revokeService;
    this.clock = clock;
    this.issuer = issuer;
    this.audience = audience;
    this.lifespanSeconds = lifespanSeconds;

    var contextInfo = new JWTAuthContextInfo();
    contextInfo.setIssuedBy(issuer);
    contextInfo.setExpectedAudience(Set.of(audience));
    contextInfo.setSignatureAlgorithm(Set.of(SIGNATURE_ALGORITHM));
    contextInfo.setClockSkew(clockSkewSeconds);
    this.parser = new DefaultJWTParser(contextInfo);
  }

  @Override
  public AccessToken issue(AuthenticatedUser user) {
    var now = clock.instant();
    var builder =
        Jwt.issuer(issuer)
            .audience(audience)
            .subject(user.id().value().toString())
            .claim(USERNAME_CLAIM, user.username())
            .claim(Claims.jti.name(), randomUUID().toString())
            .issuedAt(now)
            .expiresAt(now.plusSeconds(lifespanSeconds));
    var active = user.activeMembership();
    if (active != null) {
      builder.claim(ORG_CLAIM, active.orgId().value().toString());
      builder.claim(MEMBERSHIP_CLAIM, active.id().value().toString());
    }
    return new AccessToken(
        builder.jws().algorithm(SignatureAlgorithm.RS256).sign(keys.privateKey()));
  }

  @Override
  public AuthenticatedUser authenticate(AccessToken token)
      throws InvalidTokenException, ExpiredTokenException, RevokedTokenException {
    var jwt = verify(token);
    var jti = jwt.getTokenID();
    if (jti != null && revokeService.isRevoked(new TokenId(jti))) {
      throw new RevokedTokenException("token has been revoked");
    }
    return new AuthenticatedUser(
        new UserId(fromString(jwt.getSubject())),
        jwt.getClaim(USERNAME_CLAIM),
        activeMembership(jwt));
  }

  /// The session's active membership, or null when org-less. Both the org and membership claims are
  /// written together at mint, so a token carrying only one is malformed and rejected.
  private static Membership.@Nullable UserMembership activeMembership(JsonWebToken jwt)
      throws InvalidTokenException {
    String org = jwt.getClaim(ORG_CLAIM);
    String mid = jwt.getClaim(MEMBERSHIP_CLAIM);
    if (org == null && mid == null) {
      return null;
    }
    if (org == null || mid == null) {
      throw new InvalidTokenException("token has an incomplete active-session claim set");
    }
    return new Membership.UserMembership(
        new MembershipId(fromString(mid)), new OrgId(fromString(org)));
  }

  @Override
  public void revoke(AccessToken token) {
    JsonWebToken jwt;
    try {
      jwt = verify(token);
    } catch (DomainException e) {
      return; // expired or invalid: nothing that could still authenticate
    }
    var jti = jwt.getTokenID();
    if (jti == null) {
      return;
    }
    var ttl = Duration.between(clock.instant(), Instant.ofEpochSecond(jwt.getExpirationTime()));
    revokeService.revoke(new TokenId(jti), ttl);
  }

  private JsonWebToken verify(AccessToken token)
      throws ExpiredTokenException, InvalidTokenException {
    try {
      return parser.verify(token.value(), keys.publicKey());
    } catch (ParseException e) {
      if (e.getCause() instanceof InvalidJwtException invalid && invalid.hasExpired()) {
        throw new ExpiredTokenException("token has expired", e);
      }
      throw new InvalidTokenException("token verification failed", e);
    }
  }
}
