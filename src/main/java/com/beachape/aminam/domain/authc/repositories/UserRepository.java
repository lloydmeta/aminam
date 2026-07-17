package com.beachape.aminam.domain.authc.repositories;

import com.beachape.aminam.domain.authc.models.User;
import com.beachape.aminam.domain.authc.models.UserId;
import com.beachape.aminam.domain.errors.DomainException;
import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;

public interface UserRepository {

  User create(User user) throws DuplicateUsernameException;

  @Nullable User findByUsername(String username);

  /// Users for the given ids, in no particular order; absent ids are simply omitted.
  List<User> findByIds(Collection<UserId> ids);

  final class DuplicateUsernameException extends DomainException {
    public DuplicateUsernameException(String username) {
      super("duplicate username: " + username);
    }

    public DuplicateUsernameException(String username, Throwable cause) {
      super("duplicate username: " + username, cause);
    }
  }
}
