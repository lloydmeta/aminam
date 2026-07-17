package com.beachape.aminam.integration.app.routes.v1.auth;

import static io.restassured.RestAssured.given;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

import com.beachape.aminam.app.routes.v1.authc.models.LoginRequest;
import com.beachape.aminam.integration.utils.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
final class LoginResourceTest {

  @Test
  void validLoginReturnsTokenAndSetsHardenedCookie() {
    var username = TestHelpers.signUp();

    var response =
        given()
            .contentType(ContentType.JSON)
            .body(new LoginRequest(username, "passw0rd"))
            .when()
            .post("/api/v1/login")
            .then()
            .statusCode(200)
            .body("token", notNullValue())
            .cookie("access_token", notNullValue())
            .header("Set-Cookie", containsString("HttpOnly"))
            .header("Set-Cookie", containsString("SameSite=Strict"))
            .extract()
            .response();

    assertThat(response.jsonPath().getString("token"))
        .isEqualTo(response.getCookie("access_token"));
  }

  @Test
  void wrongPasswordReturns401() {
    var username = TestHelpers.signUp();

    given()
        .contentType(ContentType.JSON)
        .body(new LoginRequest(username, "wrong-password"))
        .when()
        .post("/api/v1/login")
        .then()
        .statusCode(401)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void unknownUserReturns401() {
    given()
        .contentType(ContentType.JSON)
        .body(new LoginRequest("ghost" + randomUUID(), "passw0rd"))
        .when()
        .post("/api/v1/login")
        .then()
        .statusCode(401)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void blankUsernameReturns400() {
    given()
        .contentType(ContentType.JSON)
        .body(new LoginRequest("", "passw0rd"))
        .when()
        .post("/api/v1/login")
        .then()
        .statusCode(400);
  }

  @Test
  void blankPasswordReturns400() {
    given()
        .contentType(ContentType.JSON)
        .body(new LoginRequest(TestHelpers.signUp(), ""))
        .when()
        .post("/api/v1/login")
        .then()
        .statusCode(400);
  }
}
