package com.beachape.aminam.app.authc.errors;

import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.app.models.ErrorResponse;
import com.beachape.aminam.domain.authc.services.AuthenticationService.InvalidCredentialsException;
import org.junit.jupiter.api.Test;

final class InvalidCredentialsExceptionMapperTest {

  private final InvalidCredentialsExceptionMapper mapper = new InvalidCredentialsExceptionMapper();

  @Test
  void mapsToUnauthorizedWithGenericMessage() {
    var response = mapper.toResponse(new InvalidCredentialsException());

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getEntity())
        .isEqualTo(new ErrorResponse("invalid username or credentials"));
  }
}
