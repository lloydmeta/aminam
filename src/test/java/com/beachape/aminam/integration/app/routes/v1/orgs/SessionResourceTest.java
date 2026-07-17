package com.beachape.aminam.integration.app.routes.v1.orgs;

import static io.restassured.RestAssured.given;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.beachape.aminam.app.routes.v1.orgs.models.SwitchOrgRequest;
import com.beachape.aminam.domain.authc.models.AccessToken;
import com.beachape.aminam.integration.utils.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
final class SessionResourceTest {

  @Test
  void switchOrgReMintsTheTokenWithTheNewActiveOrg() {
    var token = TestHelpers.newAccount().token();
    var acme = TestHelpers.createOrgAs(token, "acme");

    var response =
        TestHelpers.authed(token)
            .contentType(ContentType.JSON)
            .body(new SwitchOrgRequest(acme.value().toString()))
            .when()
            .post("/api/v1/sessions/switch-org")
            .then()
            .statusCode(200)
            .extract()
            .response();

    assertThat(response.jsonPath().getString("token"))
        .isEqualTo(response.getCookie("access_token"));

    var switched = new AccessToken(response.path("token"));

    TestHelpers.authed(switched)
        .when()
        .get("/api/v1/me")
        .then()
        .statusCode(200)
        .body("org", equalTo(acme.value().toString()));
  }

  @Test
  void switchToAnOrgYouAreNotAMemberOfReturns404Json() {
    var token = TestHelpers.newAccount().token();
    var otherOrg = TestHelpers.createOrgAs(TestHelpers.newAccount().token(), "foreign");

    TestHelpers.authed(token)
        .contentType(ContentType.JSON)
        .body(new SwitchOrgRequest(otherOrg.value().toString()))
        .when()
        .post("/api/v1/sessions/switch-org")
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void switchOrgWithoutAuthReturns401Json() {
    given()
        .contentType(ContentType.JSON)
        .body(new SwitchOrgRequest(randomUUID().toString()))
        .when()
        .post("/api/v1/sessions/switch-org")
        .then()
        .statusCode(401)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void switchOrgWithAMalformedOrgIdReturns400() {
    var token = TestHelpers.newAccount().token();

    TestHelpers.authed(token)
        .contentType(ContentType.JSON)
        .body(new SwitchOrgRequest("not-a-uuid"))
        .when()
        .post("/api/v1/sessions/switch-org")
        .then()
        .statusCode(400);
  }
}
