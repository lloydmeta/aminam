package com.beachape.aminam.domain.authc.services;

import static java.time.ZoneOffset.UTC;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.beachape.aminam.domain.authc.crypto.PasswordHasher;
import com.beachape.aminam.domain.authc.models.AccessToken;
import com.beachape.aminam.domain.authc.models.AuthenticatedUser;
import com.beachape.aminam.domain.authc.models.PasswordHash;
import com.beachape.aminam.domain.authc.models.User;
import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.authc.repositories.UserRepository;
import com.beachape.aminam.domain.authc.repositories.UserRepository.DuplicateUsernameException;
import com.beachape.aminam.domain.authc.services.AuthenticationService.InvalidCredentialsException;
import com.beachape.aminam.domain.authc.services.AuthenticationService.PasswordTooLongException;
import com.beachape.aminam.domain.authc.services.AuthenticationService.UsernameTakenException;
import com.beachape.aminam.domain.orgs.models.Membership;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.beachape.aminam.domain.orgs.services.OrganisationService;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class AuthenticationServiceTest {

  @Test
  void signupStoresHashedPasswordAndReturnsUser() throws Exception {
    var repo = new FakeUserRepository();

    var user = service(repo).signup("lloyd", "passw0rd");

    assertThat(user.username()).isEqualTo("lloyd");
    assertThat(user.passwordHash()).isEqualTo(new PasswordHash("hashed:passw0rd"));
    assertThat(user.createdAt()).isEqualTo(FIXED.instant());
    assertThat(repo.stored).containsKey("lloyd");
  }

  @Test
  void signupProvisionsAPersonalOrgForTheNewUser() throws Exception {
    var repo = new FakeUserRepository();
    var organisations = mock(OrganisationService.class);

    var user = service(repo, organisations).signup("lloyd", "passw0rd");

    verify(organisations).provisionPersonalOrg(user);
  }

  @Test
  void loginThreadsTheDefaultMembershipIntoTheToken() throws Exception {
    var repo = new FakeUserRepository();
    var organisations = mock(OrganisationService.class);
    var tokens = fakeTokenService();
    var homeOrg = new OrgId(randomUUID());
    var homeMembership =
        new Membership(
            new MembershipId(randomUUID()), new UserId(randomUUID()), homeOrg, FIXED.instant());
    var service = new AuthenticationService(repo, prefixHasher(), tokens, organisations, FIXED);
    service.signup("lloyd", "passw0rd");
    when(organisations.defaultMembership(any())).thenReturn(homeMembership);

    service.login("lloyd", "passw0rd");

    var principal = ArgumentCaptor.forClass(AuthenticatedUser.class);
    verify(tokens).issue(principal.capture());
    assertThat(principal.getValue().activeOrg()).isEqualTo(homeOrg);
    assertThat(principal.getValue().activeMembership()).isEqualTo(homeMembership.withoutUserId());
  }

  @Test
  void signupWithTakenUsernameThrowsServiceExceptionWrappingTheRepositoryCause() throws Exception {
    var repo = new FakeUserRepository();
    service(repo).signup("lloyd", "passw0rd");

    assertThatExceptionOfType(UsernameTakenException.class)
        .isThrownBy(() -> service(repo).signup("lloyd", "another passw0rd"))
        .withCauseInstanceOf(DuplicateUsernameException.class);
  }

  @Test
  void signupRejectsPasswordOver72BytesWithoutPersisting() {
    var repo = new FakeUserRepository();

    // 37 two-byte characters = 74 UTF-8 bytes, but only 37 chars
    assertThatExceptionOfType(PasswordTooLongException.class)
        .isThrownBy(() -> service(repo).signup("lloyd", "é".repeat(37)));
    assertThat(repo.stored).doesNotContainKey("lloyd");
  }

  @Test
  void signupAcceptsPasswordOfExactly72Bytes() throws Exception {
    var repo = new FakeUserRepository();

    var user = service(repo).signup("lloyd", "a".repeat(72));

    assertThat(user.username()).isEqualTo("lloyd");
  }

  @Test
  void loginWithCorrectCredentialsIssuesAToken() throws Exception {
    var repo = new FakeUserRepository();
    service(repo).signup("lloyd", "passw0rd");

    var token = service(repo).login("lloyd", "passw0rd");

    assertThat(token).isEqualTo(new AccessToken("token:lloyd"));
  }

  @Test
  void loginWithWrongPasswordThrowsInvalidCredentials() throws Exception {
    var repo = new FakeUserRepository();
    service(repo).signup("lloyd", "passw0rd");

    assertThatExceptionOfType(InvalidCredentialsException.class)
        .isThrownBy(() -> service(repo).login("lloyd", "wrong"));
  }

  @Test
  void loginWithUnknownUserThrowsInvalidCredentials() {
    var repo = new FakeUserRepository();

    assertThatExceptionOfType(InvalidCredentialsException.class)
        .isThrownBy(() -> service(repo).login("ghost", "passw0rd"));
  }

  private static final Clock FIXED = Clock.fixed(Instant.parse("2026-06-18T00:00:00Z"), UTC);

  private static final class FakeUserRepository implements UserRepository {
    final Map<String, User> stored = new HashMap<>();

    @CanIgnoreReturnValue
    @Override
    public User create(User user) throws DuplicateUsernameException {
      if (stored.containsKey(user.username())) {
        throw new DuplicateUsernameException(user.username());
      }
      stored.put(user.username(), user);
      return user;
    }

    @Override
    public @Nullable User findByUsername(String username) {
      return stored.get(username);
    }

    @Override
    public List<User> findByIds(Collection<UserId> ids) {
      return stored.values().stream().filter(u -> ids.contains(u.id())).toList();
    }
  }

  private static PasswordHasher prefixHasher() {
    PasswordHasher hasher = mock();
    when(hasher.hash(any())).thenAnswer(inv -> new PasswordHash("hashed:" + inv.getArgument(0)));
    when(hasher.verify(any(), any()))
        .thenAnswer(
            inv -> {
              String plaintext = inv.getArgument(0);
              PasswordHash hash = inv.getArgument(1);
              return hash.value().equals("hashed:" + plaintext);
            });
    return hasher;
  }

  private static TokenService fakeTokenService() {
    TokenService tokenService = mock();
    when(tokenService.issue(any()))
        .thenAnswer(
            inv -> {
              AuthenticatedUser principal = inv.getArgument(0);
              return new AccessToken("token:" + principal.getName());
            });
    return tokenService;
  }

  private static AuthenticationService service(FakeUserRepository repo) {
    return service(repo, mock(OrganisationService.class));
  }

  private static AuthenticationService service(
      FakeUserRepository repo, OrganisationService organisations) {
    return new AuthenticationService(
        repo, prefixHasher(), fakeTokenService(), organisations, FIXED);
  }
}
