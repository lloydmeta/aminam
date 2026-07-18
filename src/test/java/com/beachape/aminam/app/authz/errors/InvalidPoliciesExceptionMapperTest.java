package com.beachape.aminam.app.authz.errors;

import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.app.authz.errors.PolicyErrorsResponse.PolicyErrorItem;
import com.beachape.aminam.domain.authz.models.PolicyId.SystemPolicyId;
import com.beachape.aminam.domain.authz.services.PolicyAuthzService.InvalidPoliciesException;
import com.beachape.aminam.domain.authz.services.PolicyAuthzService.InvalidPoliciesException.Failure;
import java.util.List;
import org.junit.jupiter.api.Test;

final class InvalidPoliciesExceptionMapperTest {

  private final InvalidPoliciesExceptionMapper mapper = new InvalidPoliciesExceptionMapper();

  @Test
  void mapsToBadRequestListingEveryFailure() {
    var exception =
        new InvalidPoliciesException(
            List.of(
                new Failure(new SystemPolicyId("system:nope"), "unknown or not assignable policy"),
                new Failure(
                    new SystemPolicyId("system:gone"), "unknown or not assignable policy")));

    var response = mapper.toResponse(exception);

    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.getEntity())
        .isEqualTo(
            new PolicyErrorsResponse(
                "invalid policies",
                List.of(
                    new PolicyErrorItem("system:nope", "unknown or not assignable policy"),
                    new PolicyErrorItem("system:gone", "unknown or not assignable policy"))));
  }
}
