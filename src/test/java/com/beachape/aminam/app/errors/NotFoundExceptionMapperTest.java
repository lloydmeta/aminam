package com.beachape.aminam.app.errors;

import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.app.models.ErrorResponse;
import com.beachape.aminam.domain.errors.NotFoundException;
import org.junit.jupiter.api.Test;

final class NotFoundExceptionMapperTest {

  private final NotFoundExceptionMapper mapper = new NotFoundExceptionMapper();

  @Test
  void mapsToNotFoundWithDatabaseMessage() {
    var response = mapper.toResponse(new NotFoundException(NotFoundException.Type.DATABASE));

    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(response.getEntity()).isEqualTo(new ErrorResponse("database not found"));
  }

  @Test
  void mapsToNotFoundWithMemberMessage() {
    var response = mapper.toResponse(new NotFoundException(NotFoundException.Type.MEMBER));

    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(response.getEntity()).isEqualTo(new ErrorResponse("member not found"));
  }

  @Test
  void mapsToNotFoundWithOrganisationMessage() {
    var response = mapper.toResponse(new NotFoundException(NotFoundException.Type.ORGANISATION));

    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(response.getEntity()).isEqualTo(new ErrorResponse("organisation not found"));
  }

  @Test
  void mapsToNotFoundWithPolicyMessage() {
    var response = mapper.toResponse(new NotFoundException(NotFoundException.Type.POLICY));

    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(response.getEntity()).isEqualTo(new ErrorResponse("policy not found"));
  }

  @Test
  void mapsToNotFoundWithUserMessage() {
    var response = mapper.toResponse(new NotFoundException(NotFoundException.Type.USER));

    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(response.getEntity()).isEqualTo(new ErrorResponse("user not found"));
  }
}
