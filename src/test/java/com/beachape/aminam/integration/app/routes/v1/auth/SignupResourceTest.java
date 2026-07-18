package com.beachape.aminam.integration.app.routes.v1.auth;

import static io.restassured.RestAssured.given;
import static java.util.UUID.randomUUID;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import com.beachape.aminam.app.routes.v1.authc.models.SignupRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;

@QuarkusTest
final class SignupResourceTest {

  @Test
  void validSignupReturns201WithIdAndUsernameAndNoHash() {
    var username = uniqueUsername();

    signup(username, "passw0rd")
        .statusCode(201)
        .body("id", notNullValue())
        .body("username", equalTo(username))
        .body("$", not(hasKey("passwordHash")));
  }

  @Test
  void duplicateUsernameReturns409Json() {
    var username = uniqueUsername();

    signup(username, "passw0rd").statusCode(201);
    signup(username, "passw0rd")
        .statusCode(409)
        .contentType(ContentType.JSON)
        .body("message", equalTo("username already taken"));
  }

  @Test
  void blankUsernameReturns400() {
    signup("", "passw0rd").statusCode(400);
  }

  @Test
  void invalidUsernameCharactersReturns400() {
    signup("bad name!", "passw0rd").statusCode(400);
  }

  @Test
  void shortPasswordReturns400() {
    signup(uniqueUsername(), "short").statusCode(400);
  }

  @Test
  void multibytePasswordOver72BytesReturns400() {
    signup(uniqueUsername(), "é".repeat(37)).statusCode(400);
  }

  private static String uniqueUsername() {
    return "user" + randomUUID().toString().replace("-", "");
  }

  private static ValidatableResponse signup(String username, String password) {
    return given()
        .contentType(ContentType.JSON)
        .body(new SignupRequest(username, password))
        .when()
        .post("/api/v1/signup")
        .then();
  }
}
