package com.beachape.aminam.app.authc.errors;

import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.app.models.ErrorResponse;
import com.beachape.aminam.domain.authc.repositories.UserRepository.DuplicateUsernameException;
import com.beachape.aminam.domain.authc.services.AuthenticationService.UsernameTakenException;
import org.junit.jupiter.api.Test;

final class UsernameTakenExceptionMapperTest {

  private final UsernameTakenExceptionMapper mapper = new UsernameTakenExceptionMapper();

  @Test
  void mapsToConflictWithGenericMessage() {
    var exception = new UsernameTakenException("lloyd", new DuplicateUsernameException("lloyd"));

    var response = mapper.toResponse(exception);

    assertThat(response.getStatus()).isEqualTo(409);
    assertThat(response.getEntity()).isEqualTo(new ErrorResponse("username already taken"));
  }
}
