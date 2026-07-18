package com.beachape.aminam.app.authz.errors;

import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.app.models.ErrorResponse;
import com.beachape.aminam.domain.authz.models.PolicyId.MalformedPolicyIdException;
import org.junit.jupiter.api.Test;

final class MalformedPolicyIdExceptionMapperTest {

  private final MalformedPolicyIdExceptionMapper mapper = new MalformedPolicyIdExceptionMapper();

  @Test
  void mapsToBadRequestJson() {
    var response =
        mapper.toResponse(new MalformedPolicyIdException("bogus", new IllegalArgumentException()));

    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.getEntity()).isEqualTo(new ErrorResponse("malformed policy id: bogus"));
  }
}
