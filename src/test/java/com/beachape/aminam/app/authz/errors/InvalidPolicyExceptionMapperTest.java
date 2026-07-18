package com.beachape.aminam.app.authz.errors;

import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.app.authz.errors.PolicyValidationErrorsResponse.PolicyValidationError;
import com.beachape.aminam.domain.authz.services.PolicyValidator.InvalidPolicyException;
import com.beachape.aminam.domain.authz.services.PolicyValidator.InvalidPolicyException.Failure;
import java.util.List;
import org.junit.jupiter.api.Test;

final class InvalidPolicyExceptionMapperTest {

  private final InvalidPolicyExceptionMapper mapper = new InvalidPolicyExceptionMapper();

  @Test
  void mapsToBadRequestListingEveryFailure() {
    var exception =
        new InvalidPolicyException(
            List.of(
                new Failure("statements[0].condition", "ERROR: bad"),
                new Failure("statements[1].actions", "must not be empty")));

    var response = mapper.toResponse(exception);

    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.getEntity())
        .isEqualTo(
            new PolicyValidationErrorsResponse(
                "invalid policy",
                List.of(
                    new PolicyValidationError("statements[0].condition", "ERROR: bad"),
                    new PolicyValidationError("statements[1].actions", "must not be empty"))));
  }
}
