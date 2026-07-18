package com.beachape.aminam.integration.app.routes.v1.auth;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

import com.beachape.aminam.integration.utils.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
final class LogoutResourceTest {

  @Test
  void logoutWithBearerRevokesTokenSoSubsequentRequestReturns401() {
    var account = TestHelpers.newAccount();

    TestHelpers.authed(account.token()).when().post("/api/v1/logout").then().statusCode(200);

    TestHelpers.authed(account.token()).when().get("/api/v1/me").then().statusCode(401);
  }

  @Test
  void logoutWithCookieRevokesTokenSoSubsequentRequestReturns401() {
    var account = TestHelpers.newAccount();

    given()
        .cookie("access_token", account.token().value())
        .when()
        .post("/api/v1/logout")
        .then()
        .statusCode(200);

    given()
        .cookie("access_token", account.token().value())
        .when()
        .get("/api/v1/me")
        .then()
        .statusCode(401);
  }

  @Test
  void logoutClearsCookie() {
    var account = TestHelpers.newAccount();

    TestHelpers.authed(account.token())
        .when()
        .post("/api/v1/logout")
        .then()
        .statusCode(200)
        .header("Set-Cookie", containsString("Max-Age=0"))
        .header("Set-Cookie", containsString("HttpOnly"))
        .header("Set-Cookie", containsString("SameSite=Strict"));
  }

  @Test
  void unauthenticatedLogoutReturns401Json() {
    given()
        .when()
        .post("/api/v1/logout")
        .then()
        .statusCode(401)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }
}
