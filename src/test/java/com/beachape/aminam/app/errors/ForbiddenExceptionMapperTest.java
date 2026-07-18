package com.beachape.aminam.app.errors;

import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.app.models.ErrorResponse;
import io.quarkus.security.ForbiddenException;
import org.junit.jupiter.api.Test;

final class ForbiddenExceptionMapperTest {

  private final ForbiddenExceptionMapper mapper = new ForbiddenExceptionMapper();

  @Test
  void mapsToForbiddenWithGenericMessage() {
    var response = mapper.toResponse(new ForbiddenException("denied"));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getEntity()).isEqualTo(new ErrorResponse("access denied"));
  }
}
