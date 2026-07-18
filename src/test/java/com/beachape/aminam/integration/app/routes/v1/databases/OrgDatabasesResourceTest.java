package com.beachape.aminam.integration.app.routes.v1.databases;

import static io.restassured.RestAssured.given;
import static java.util.UUID.randomUUID;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.beachape.aminam.app.routes.v1.databases.models.CreateDatabaseRequest;
import com.beachape.aminam.integration.utils.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/// Endpoint mechanics for `/orgs/{id}/databases`. The authz decision matrix lives in
/// DatabaseAccessAuthzTest.
@QuarkusTest
final class OrgDatabasesResourceTest {

  @Test
  void managerCreatesADatabase() {
    var org = TestHelpers.managedOrg();

    TestHelpers.authed(org.token())
        .contentType(ContentType.JSON)
        .body(new CreateDatabaseRequest("metrics"))
        .when()
        .post("/api/v1/orgs/" + org.id() + "/databases")
        .then()
        .statusCode(201)
        .contentType(ContentType.JSON)
        .body("name", equalTo("metrics"))
        .body("orgId", equalTo(org.id().toString()))
        .body("editable", equalTo(true))
        .body("id", notNullValue())
        .body("createdBy", notNullValue())
        .body("createdAt", notNullValue());
  }

  @Test
  void listReturnsDatabasesInCreationOrder() {
    var org = TestHelpers.managedOrg();
    TestHelpers.createDatabaseIn(org, "ledger");
    TestHelpers.createDatabaseIn(org, "warehouse");

    TestHelpers.listDatabasesAs(org.token(), org.id())
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("values.name", contains("ledger", "warehouse"));
  }

  @Test
  void createWithoutAuthReturns401Json() {
    given()
        .contentType(ContentType.JSON)
        .body(new CreateDatabaseRequest("metrics"))
        .when()
        .post("/api/v1/orgs/" + randomUUID() + "/databases")
        .then()
        .statusCode(401)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void createWithBlankNameReturns400() {
    var org = TestHelpers.managedOrg();

    TestHelpers.authed(org.token())
        .contentType(ContentType.JSON)
        .body(new CreateDatabaseRequest(""))
        .when()
        .post("/api/v1/orgs/" + org.id() + "/databases")
        .then()
        .statusCode(400);
  }
}
