package com.beachape.aminam.app.orgs.errors;

import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.app.models.ErrorResponse;
import com.beachape.aminam.domain.orgs.services.OrganisationService.MemberAlreadyExistsException;
import org.junit.jupiter.api.Test;

final class MemberAlreadyExistsExceptionMapperTest {

  private final MemberAlreadyExistsExceptionMapper mapper =
      new MemberAlreadyExistsExceptionMapper();

  @Test
  void mapsToConflictWithGenericMessage() {
    var response = mapper.toResponse(new MemberAlreadyExistsException("lloyd"));

    assertThat(response.getStatus()).isEqualTo(409);
    assertThat(response.getEntity()).isEqualTo(new ErrorResponse("already a member"));
  }
}
