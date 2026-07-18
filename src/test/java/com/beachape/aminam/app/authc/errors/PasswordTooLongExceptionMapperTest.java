package com.beachape.aminam.app.authc.errors;

import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.app.models.ErrorResponse;
import com.beachape.aminam.domain.authc.services.AuthenticationService.PasswordTooLongException;
import org.junit.jupiter.api.Test;

final class PasswordTooLongExceptionMapperTest {

  private final PasswordTooLongExceptionMapper mapper = new PasswordTooLongExceptionMapper();

  @Test
  void mapsToBadRequestWithMessage() {
    var response = mapper.toResponse(new PasswordTooLongException(72));

    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.getEntity())
        .isEqualTo(new ErrorResponse("password must be at most 72 bytes"));
  }
}
