package com.beachape.aminam.app.errors;

import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.app.models.ErrorResponse;
import com.beachape.aminam.domain.errors.NotAuthorisedException;
import org.junit.jupiter.api.Test;

final class NotAuthorisedExceptionMapperTest {

  private final NotAuthorisedExceptionMapper mapper = new NotAuthorisedExceptionMapper();

  @Test
  void mapsToForbiddenWithNotAuthorisedMessage() {
    var response = mapper.toResponse(new NotAuthorisedException());

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getEntity()).isEqualTo(new ErrorResponse("not authorised"));
  }
}
