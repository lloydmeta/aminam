package com.beachape.aminam.integration.authz;

import static java.util.UUID.randomUUID;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.beachape.aminam.app.routes.v1.databases.models.CreateDatabaseRequest;
import com.beachape.aminam.domain.authz.services.SystemPolicies;
import com.beachape.aminam.domain.databases.models.DatabaseId;
import com.beachape.aminam.integration.utils.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.List;
import org.junit.jupiter.api.Test;

// The database access decision matrix at the HTTP boundary. Sibling of OrgAccessAuthzTest.
@QuarkusTest
final class DatabaseAccessAuthzTest {

  @Test
  void managerReadsAndEditsItsDatabase() {
    var db = managedDatabase();

    TestHelpers.readDatabaseAs(db.org().token(), db.dbId())
        .statusCode(200)
        .body("editable", equalTo(true));

    TestHelpers.editDatabaseAs(db.org().token(), db.dbId(), "renamed").statusCode(200);
  }

  @Test
  void adminReadsAndEdits() {
    var db = managedDatabase();
    var admin = TestHelpers.createNewUserAsMemberIn(db.org(), List.of(SystemPolicies.ADMIN));

    TestHelpers.readDatabaseAs(admin, db.dbId()).statusCode(200).body("editable", equalTo(true));

    TestHelpers.editDatabaseAs(admin, db.dbId(), "renamed").statusCode(200);
  }

  @Test
  void viewerReadsButCannotEdit() {
    var db = managedDatabase();
    var viewer = TestHelpers.createNewUserAsMemberIn(db.org(), List.of(SystemPolicies.VIEWER));

    TestHelpers.readDatabaseAs(viewer, db.dbId()).statusCode(200).body("editable", equalTo(false));

    TestHelpers.editDatabaseAs(viewer, db.dbId(), "renamed")
        .statusCode(403)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void memberWithNoPolicyCannotReadTheDatabase404() {
    var db = managedDatabase();
    var member = TestHelpers.createNewUserAsMemberIn(db.org(), List.of());

    TestHelpers.readDatabaseAs(member, db.dbId())
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void memberWithNoPolicyCannotListDatabases404() {
    var db = managedDatabase();
    var member = TestHelpers.createNewUserAsMemberIn(db.org(), List.of());

    TestHelpers.listDatabasesAs(member, db.org().id())
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void nonMemberCannotAccessTheDatabase404() {
    var db = managedDatabase();
    var outsider = TestHelpers.newAccount();

    TestHelpers.readDatabaseAs(outsider.token(), db.dbId())
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());

    TestHelpers.editDatabaseAs(outsider.token(), db.dbId(), "renamed").statusCode(404);

    TestHelpers.authed(outsider.token())
        .when()
        .delete("/api/v1/databases/" + db.dbId())
        .then()
        .statusCode(404);
  }

  @Test
  void nonMemberCannotListDatabases404() {
    var db = managedDatabase();
    var outsider = TestHelpers.newAccount();

    TestHelpers.listDatabasesAs(outsider.token(), db.org().id())
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void nonMemberCannotCreateInTheOrg404() {
    var owner = TestHelpers.newAccount();
    var orgId = TestHelpers.createOrgAs(owner.token(), "acme");
    var outsider = TestHelpers.newAccount();

    TestHelpers.authed(outsider.token())
        .contentType(ContentType.JSON)
        .body(new CreateDatabaseRequest("metrics"))
        .when()
        .post("/api/v1/orgs/" + orgId + "/databases")
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void memberActiveElsewhereCannotCreate404() {
    var org = TestHelpers.managedOrg();
    // A manager of acme who has NOT switched into it: their session is still in their personal org,
    // so acme is cross-org and invisible. Pins the path-vs-session confinement (404, not 403 or
    // 201).
    var member = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(org, member.username(), List.of(SystemPolicies.MANAGER));

    TestHelpers.authed(member.token())
        .contentType(ContentType.JSON)
        .body(new CreateDatabaseRequest("metrics"))
        .when()
        .post("/api/v1/orgs/" + org.id() + "/databases")
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void memberActiveElsewhereCannotListDatabases404() {
    var org = TestHelpers.managedOrg();
    var member = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(org, member.username(), List.of(SystemPolicies.MANAGER));

    TestHelpers.listDatabasesAs(member.token(), org.id())
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void viewerCannotCreate403() {
    var org = TestHelpers.managedOrg();
    var viewer = TestHelpers.createNewUserAsMemberIn(org, List.of(SystemPolicies.VIEWER));

    TestHelpers.authed(viewer)
        .contentType(ContentType.JSON)
        .body(new CreateDatabaseRequest("metrics"))
        .when()
        .post("/api/v1/orgs/" + org.id() + "/databases")
        .then()
        .statusCode(403)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void unknownDatabaseIs404() {
    var account = TestHelpers.newAccount();

    TestHelpers.readDatabaseAs(account.token(), new DatabaseId(randomUUID()))
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void listEditabilityFollowsTheRole() {
    var org = TestHelpers.managedOrg();
    TestHelpers.createDatabaseIn(org, "a");
    TestHelpers.createDatabaseIn(org, "b");

    TestHelpers.listDatabasesAs(org.token(), org.id())
        .statusCode(200)
        .body("values.editable", equalTo(List.of(true, true)));

    var viewer = TestHelpers.createNewUserAsMemberIn(org, List.of(SystemPolicies.VIEWER));
    TestHelpers.listDatabasesAs(viewer, org.id())
        .statusCode(200)
        .body("values.editable", equalTo(List.of(false, false)));
  }

  // <-- multi -->

  @Test
  void viewerPlusAdminReadsAndEdits() {
    var db = managedDatabase();
    var member =
        TestHelpers.createNewUserAsMemberIn(
            db.org(), List.of(SystemPolicies.VIEWER, SystemPolicies.ADMIN));

    // Viewer alone is read-only; admin adds database:update, so the union is editable.
    TestHelpers.readDatabaseAs(member, db.dbId()).statusCode(200).body("editable", equalTo(true));

    TestHelpers.editDatabaseAs(member, db.dbId(), "renamed").statusCode(200);
  }

  @Test
  void viewerPlusManagerCanCreate() {
    var org = TestHelpers.managedOrg();
    var member =
        TestHelpers.createNewUserAsMemberIn(
            org, List.of(SystemPolicies.VIEWER, SystemPolicies.MANAGER));

    // Viewer alone cannot create (403); manager grants database:create, so the union can.
    TestHelpers.authed(member)
        .contentType(ContentType.JSON)
        .body(new CreateDatabaseRequest("metrics"))
        .when()
        .post("/api/v1/orgs/" + org.id() + "/databases")
        .then()
        .statusCode(201);
  }

  // <-- /multi -->

  private static Fixture managedDatabase() {
    var org = TestHelpers.managedOrg();
    var dbId = TestHelpers.createDatabaseIn(org, "metrics");
    return new Fixture(org, dbId);
  }

  private record Fixture(TestHelpers.OrgSession org, DatabaseId dbId) {}
}
