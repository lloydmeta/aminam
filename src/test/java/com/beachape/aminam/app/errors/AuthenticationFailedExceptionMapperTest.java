package com.beachape.aminam.app.errors;

import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.app.models.ErrorResponse;
import io.quarkus.security.AuthenticationFailedException;
import org.junit.jupiter.api.Test;

final class AuthenticationFailedExceptionMapperTest {

  private final AuthenticationFailedExceptionMapper mapper =
      new AuthenticationFailedExceptionMapper();

  @Test
  void mapsToUnauthorizedWithGenericMessage() {
    var response = mapper.toResponse(new AuthenticationFailedException("token expired"));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getEntity()).isEqualTo(new ErrorResponse("invalid or expired token"));
  }
}
