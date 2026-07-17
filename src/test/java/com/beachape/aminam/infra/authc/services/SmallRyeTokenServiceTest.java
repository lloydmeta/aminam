package com.beachape.aminam.infra.authc.services;

import static java.time.ZoneOffset.UTC;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.beachape.aminam.domain.authc.models.AccessToken;
import com.beachape.aminam.domain.authc.models.AuthenticatedUser;
import com.beachape.aminam.domain.authc.models.TokenId;
import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.authc.services.TokenRevocationService;
import com.beachape.aminam.domain.authc.services.TokenService.ExpiredTokenException;
import com.beachape.aminam.domain.authc.services.TokenService.InvalidTokenException;
import com.beachape.aminam.domain.authc.services.TokenService.RevokedTokenException;
import com.beachape.aminam.domain.orgs.models.Membership;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.build.Jwt;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class SmallRyeTokenServiceTest {

  // jose4j checks expiry against the wall clock, so valid-token cases use the real clock.
  @SuppressWarnings("TimeZoneUsage")
  private static final Clock SYSTEM = Clock.systemUTC();

  private static final Clock LONG_AGO = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), UTC);
  private static final String ISSUER = "https://aminam.test";
  private static final String AUDIENCE = "aminam";

  @Test
  void issueThenAuthenticateRoundTripsThePrincipal() throws Exception {
    var service = service(keys(), mockRevocationService(), SYSTEM, ISSUER);
    var id = new UserId(randomUUID());

    var principal = service.authenticate(service.issue(new AuthenticatedUser(id, "lloyd")));

    assertThat(principal.id()).isEqualTo(id);
    assertThat(principal.username()).isEqualTo("lloyd");
  }

  @Test
  void issueThenAuthenticateRoundTripsTheActiveMembership() throws Exception {
    var service = service(keys(), mockRevocationService(), SYSTEM, ISSUER);
    var active =
        new Membership.UserMembership(new MembershipId(randomUUID()), new OrgId(randomUUID()));

    var principal =
        service.authenticate(
            service.issue(new AuthenticatedUser(new UserId(randomUUID()), "lloyd", active)));

    assertThat(principal.activeMembership()).isEqualTo(active);
  }

  @Test
  void aTokenWithoutAnOrgClaimHasNoActiveMembership() throws Exception {
    var service = service(keys(), mockRevocationService(), SYSTEM, ISSUER);

    var principal =
        service.authenticate(
            service.issue(new AuthenticatedUser(new UserId(randomUUID()), "lloyd")));

    assertThat(principal.activeMembership()).isNull();
  }

  @Test
  void aTokenWithOnlyOneSessionClaimIsRejected() {
    var keys = keys();
    var now = SYSTEM.instant();
    var token =
        new AccessToken(
            Jwt.issuer(ISSUER)
                .audience(AUDIENCE)
                .subject(randomUUID().toString())
                .claim("username", "lloyd")
                .claim("org", randomUUID().toString()) // org without the matching mid claim
                .claim("jti", randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(1800))
                .jws()
                .algorithm(SignatureAlgorithm.RS256)
                .sign(keys.privateKey()));
    var service = service(keys, mockRevocationService(), SYSTEM, ISSUER);

    assertThatExceptionOfType(InvalidTokenException.class)
        .isThrownBy(() -> service.authenticate(token));
  }

  @Test
  void revokedTokenIsRejected() {
    var service = service(keys(), mockRevocationService(), SYSTEM, ISSUER);
    var token = service.issue(new AuthenticatedUser(new UserId(randomUUID()), "lloyd"));

    service.revoke(token);

    assertThatExceptionOfType(RevokedTokenException.class)
        .isThrownBy(() -> service.authenticate(token));
  }

  @Test
  void tamperedTokenIsRejected() {
    var service = service(keys(), mockRevocationService(), SYSTEM, ISSUER);
    var token = service.issue(new AuthenticatedUser(new UserId(randomUUID()), "lloyd"));

    assertThatExceptionOfType(InvalidTokenException.class)
        .isThrownBy(() -> service.authenticate(new AccessToken(token.value() + "x")));
  }

  @Test
  void tokenFromAnotherIssuerIsRejected() {
    var keys = keys();
    var revokeService = mockRevocationService();
    var token =
        service(keys, revokeService, SYSTEM, "https://evil.test")
            .issue(new AuthenticatedUser(new UserId(randomUUID()), "lloyd"));

    assertThatExceptionOfType(InvalidTokenException.class)
        .isThrownBy(() -> service(keys, revokeService, SYSTEM, ISSUER).authenticate(token));
  }

  @Test
  void expiredTokenIsRejected() {
    var keys = keys();
    var revokeService = mockRevocationService();
    var token =
        service(keys, revokeService, LONG_AGO, ISSUER)
            .issue(new AuthenticatedUser(new UserId(randomUUID()), "lloyd"));

    assertThatExceptionOfType(ExpiredTokenException.class)
        .isThrownBy(() -> service(keys, revokeService, SYSTEM, ISSUER).authenticate(token));
  }

  @Test
  void tokenForAnotherAudienceIsRejected() {
    var keys = keys();
    var revokeService = mockRevocationService();
    var token =
        service(keys, revokeService, SYSTEM, ISSUER, "other-audience")
            .issue(new AuthenticatedUser(new UserId(randomUUID()), "lloyd"));

    assertThatExceptionOfType(InvalidTokenException.class)
        .isThrownBy(
            () -> service(keys, revokeService, SYSTEM, ISSUER, AUDIENCE).authenticate(token));
  }

  @Test
  void revokingAnInvalidTokenIsANoOp() {
    var revokeService = mockRevocationService();
    var service = service(keys(), revokeService, SYSTEM, ISSUER);

    service.revoke(new AccessToken("not-a-jwt"));

    verify(revokeService, never()).revoke(any(), any());
  }

  private static JwtKeyProvider keys() {
    return new JwtKeyProvider(Optional.<String>empty(), Optional.<String>empty());
  }

  private static SmallRyeTokenService service(
      JwtKeyProvider keys, TokenRevocationService revokeService, Clock clock, String issuer) {
    return service(keys, revokeService, clock, issuer, AUDIENCE);
  }

  private static SmallRyeTokenService service(
      JwtKeyProvider keys,
      TokenRevocationService revokeService,
      Clock clock,
      String issuer,
      String audience) {
    return new SmallRyeTokenService(keys, revokeService, clock, issuer, audience, 1800L, 30);
  }

  private static TokenRevocationService mockRevocationService() {
    Set<TokenId> revoked = new HashSet<>();
    TokenRevocationService revokeService = mock();
    doAnswer(
            invocation -> {
              revoked.add(invocation.getArgument(0));
              return null;
            })
        .when(revokeService)
        .revoke(any(TokenId.class), any(Duration.class));
    when(revokeService.isRevoked(any(TokenId.class)))
        .thenAnswer(invocation -> revoked.contains(invocation.getArgument(0)));
    return revokeService;
  }
}
