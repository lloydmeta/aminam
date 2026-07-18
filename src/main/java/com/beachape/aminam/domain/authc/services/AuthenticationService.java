package com.beachape.aminam.domain.authc.services;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.UUID.randomUUID;

import com.beachape.aminam.domain.authc.crypto.PasswordHasher;
import com.beachape.aminam.domain.authc.models.AccessToken;
import com.beachape.aminam.domain.authc.models.AuthenticatedUser;
import com.beachape.aminam.domain.authc.models.PasswordHash;
import com.beachape.aminam.domain.authc.models.User;
import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.authc.repositories.UserRepository;
import com.beachape.aminam.domain.authc.repositories.UserRepository.DuplicateUsernameException;
import com.beachape.aminam.domain.errors.DomainException;
import com.beachape.aminam.domain.orgs.services.OrganisationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;

@ApplicationScoped
public class AuthenticationService {

  /// bcrypt silently truncates beyond 72 bytes, so anything longer is rejected outright.
  static final int MAX_PASSWORD_BYTES = 72;

  public static final class PasswordTooLongException extends DomainException {
    public PasswordTooLongException(int maxBytes) {
      super("password exceeds the maximum of " + maxBytes + " bytes");
    }
  }

  public static final class UsernameTakenException extends DomainException {
    public UsernameTakenException(String username, Throwable cause) {
      super("username already taken: " + username, cause);
    }
  }

  public static final class InvalidCredentialsException extends DomainException {
    public InvalidCredentialsException() {
      super("invalid username or credentials");
    }
  }

  private final UserRepository users;
  private final PasswordHasher hasher;
  private final TokenService tokens;
  private final OrganisationService organisations;
  private final Clock clock;

  private final PasswordHash dummyHash;

  @Inject
  AuthenticationService(
      UserRepository users,
      PasswordHasher hasher,
      TokenService tokens,
      OrganisationService organisations,
      Clock clock) {
    this.users = users;
    this.hasher = hasher;
    this.tokens = tokens;
    this.organisations = organisations;
    this.clock = clock;
    this.dummyHash = hasher.hash("absent-user-placeholder");
  }

  @Transactional(rollbackOn = DomainException.class)
  public User signup(String username, String password)
      throws PasswordTooLongException, UsernameTakenException {
    if (password.getBytes(UTF_8).length > MAX_PASSWORD_BYTES) {
      throw new PasswordTooLongException(MAX_PASSWORD_BYTES);
    }
    var user = new User(new UserId(randomUUID()), username, hasher.hash(password), clock.instant());
    User created;
    try {
      created = users.create(user);
    } catch (DuplicateUsernameException e) {
      throw new UsernameTakenException(username, e);
    }
    organisations.provisionPersonalOrg(created);
    return created;
  }

  public AccessToken login(String username, String password) throws InvalidCredentialsException {
    var user = users.findByUsername(username);
    if (user == null) {
      hasher.verify(password, dummyHash); // spend the same time as a real verification
      throw new InvalidCredentialsException();
    }
    if (!hasher.verify(password, user.passwordHash())) {
      throw new InvalidCredentialsException();
    }
    var home = organisations.defaultMembership(user.id());
    var active = home == null ? null : home.withoutUserId();
    return tokens.issue(new AuthenticatedUser(user.id(), user.username(), active));
  }
}
