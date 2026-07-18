package com.beachape.aminam.integration.app.routes.v1.orgs;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

import com.beachape.aminam.app.routes.v1.orgs.models.CreateOrgRequest;
import com.beachape.aminam.integration.utils.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

@QuarkusTest
final class OrgResourceTest {

  @Test
  void createOrgReturns201WithBody() {
    var account = TestHelpers.newAccount();

    TestHelpers.authed(account.token())
        .contentType(ContentType.JSON)
        .body(new CreateOrgRequest("acme"))
        .when()
        .post("/api/v1/orgs")
        .then()
        .statusCode(201)
        .body("id", notNullValue())
        .body("name", equalTo("acme"));
  }

  @Test
  void signupProvisionsAPersonalOrgVisibleInTheList() {
    var account = TestHelpers.newAccount();

    TestHelpers.authed(account.token())
        .when()
        .get("/api/v1/orgs")
        .then()
        .statusCode(200)
        .body("values.name", hasItem(account.username()));
  }

  @Test
  void listReturnsOrgsInJoinOrder() {
    var account = TestHelpers.newAccount();
    TestHelpers.createOrgAs(account.token(), "acme");

    TestHelpers.authed(account.token())
        .when()
        .get("/api/v1/orgs")
        .then()
        .statusCode(200)
        .body("values.name", contains(account.username(), "acme"));
  }

  @Test
  void createWithBlankNameReturns400() {
    var account = TestHelpers.newAccount();

    TestHelpers.authed(account.token())
        .contentType(ContentType.JSON)
        .body(new CreateOrgRequest(""))
        .when()
        .post("/api/v1/orgs")
        .then()
        .statusCode(400);
  }

  @Test
  void createWithoutAuthReturns401Json() {
    given()
        .contentType(ContentType.JSON)
        .body(new CreateOrgRequest("acme"))
        .when()
        .post("/api/v1/orgs")
        .then()
        .statusCode(401)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }
}
