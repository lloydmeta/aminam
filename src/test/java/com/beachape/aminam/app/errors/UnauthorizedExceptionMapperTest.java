package com.beachape.aminam.app.errors;

import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.app.models.ErrorResponse;
import io.quarkus.security.UnauthorizedException;
import org.junit.jupiter.api.Test;

final class UnauthorizedExceptionMapperTest {

  private final UnauthorizedExceptionMapper mapper = new UnauthorizedExceptionMapper();

  @Test
  void mapsToUnauthorizedWithGenericMessage() {
    var response = mapper.toResponse(new UnauthorizedException());

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getEntity()).isEqualTo(new ErrorResponse("authentication required"));
  }
}
