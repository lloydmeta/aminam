package com.beachape.aminam.integration.app.routes.v1.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.beachape.aminam.integration.utils.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
final class MeResourceTest {

  @Test
  void bearerTokenReturnsIdAndUsername() {
    var account = TestHelpers.newAccount();

    TestHelpers.authed(account.token())
        .when()
        .get("/api/v1/me")
        .then()
        .statusCode(200)
        .body("id", notNullValue())
        .body("username", equalTo(account.username()));
  }

  @Test
  void loggedInUserHasAnActiveOrg() {
    var account = TestHelpers.newAccount();

    TestHelpers.authed(account.token())
        .when()
        .get("/api/v1/me")
        .then()
        .statusCode(200)
        .body("org", notNullValue());
  }

  @Test
  void cookieTokenReturnsIdentity() {
    var account = TestHelpers.newAccount();

    given()
        .cookie("access_token", account.token().value())
        .when()
        .get("/api/v1/me")
        .then()
        .statusCode(200)
        .body("username", equalTo(account.username()));
  }

  @Test
  void noAuthReturns401Json() {
    given()
        .when()
        .get("/api/v1/me")
        .then()
        .statusCode(401)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void invalidBearerTokenReturns401Json() {
    given()
        .header("Authorization", "Bearer not-a-jwt")
        .when()
        .get("/api/v1/me")
        .then()
        .statusCode(401)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void basicAuthIsUnsupportedAndReturns401Json() {
    // Basic auth is not a supported scheme, so the header is ignored and the request is anonymous.
    given()
        .header("Authorization", "Basic bm9jb2xvbg==")
        .when()
        .get("/api/v1/me")
        .then()
        .statusCode(401)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }
}
