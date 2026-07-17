package com.beachape.aminam.integration.app.routes.v1.databases;

import static io.restassured.RestAssured.given;
import static java.util.UUID.randomUUID;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.beachape.aminam.app.routes.v1.databases.models.UpdateDatabaseRequest;
import com.beachape.aminam.integration.utils.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

/// Endpoint mechanics for `/databases/{id}`. The authz decision matrix lives in
/// DatabaseAccessAuthzTest.
@QuarkusTest
final class DatabaseResourceTest {

  @Test
  void managerGetsAnEditableDatabase() {
    var org = TestHelpers.managedOrg();
    var id = TestHelpers.createDatabaseIn(org, "metrics");
    // The same session created the org and the database, so the two creators must agree.
    String creator =
        TestHelpers.authed(org.token())
            .when()
            .get("/api/v1/orgs/" + org.id())
            .then()
            .statusCode(200)
            .extract()
            .path("createdBy");

    TestHelpers.authed(org.token())
        .when()
        .get("/api/v1/databases/" + id)
        .then()
        .statusCode(200)
        .body("id", equalTo(id.toString()))
        .body("name", equalTo("metrics"))
        .body("createdBy", equalTo(creator))
        .body("editable", equalTo(true));
  }

  @Test
  void managerUpdatesTheName() {
    var org = TestHelpers.managedOrg();
    var id = TestHelpers.createDatabaseIn(org, "metrics");

    TestHelpers.authed(org.token())
        .contentType(ContentType.JSON)
        .body(new UpdateDatabaseRequest("renamed"))
        .when()
        .put("/api/v1/databases/" + id)
        .then()
        .statusCode(200)
        .body("name", equalTo("renamed"))
        .body("editable", equalTo(true));

    TestHelpers.authed(org.token())
        .when()
        .get("/api/v1/databases/" + id)
        .then()
        .statusCode(200)
        .body("name", equalTo("renamed"));
  }

  @Test
  void managerDeletesTheDatabase() {
    var org = TestHelpers.managedOrg();
    var id = TestHelpers.createDatabaseIn(org, "metrics");

    TestHelpers.authed(org.token())
        .when()
        .delete("/api/v1/databases/" + id)
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON);

    TestHelpers.authed(org.token())
        .when()
        .get("/api/v1/databases/" + id)
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void getWithoutAuthReturns401Json() {
    given()
        .when()
        .get("/api/v1/databases/" + randomUUID())
        .then()
        .statusCode(401)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void updateWithBlankNameReturns400() {
    var org = TestHelpers.managedOrg();
    var id = TestHelpers.createDatabaseIn(org, "metrics");

    TestHelpers.authed(org.token())
        .contentType(ContentType.JSON)
        .body(new UpdateDatabaseRequest(""))
        .when()
        .put("/api/v1/databases/" + id)
        .then()
        .statusCode(400);
  }
}
