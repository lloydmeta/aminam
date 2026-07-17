package com.beachape.aminam.integration.app.routes.v1.orgs;

import static io.restassured.RestAssured.given;
import static java.util.UUID.randomUUID;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;

import com.beachape.aminam.app.routes.v1.orgs.models.AddMemberRequest;
import com.beachape.aminam.app.routes.v1.orgs.models.PolicyIdsRequest;
import com.beachape.aminam.app.routes.v1.orgs.models.SwitchOrgRequest;
import com.beachape.aminam.domain.authz.models.PolicyId.SystemPolicyId;
import com.beachape.aminam.integration.utils.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
final class MembersResourceTest {

  @Test
  void managerAddsAMemberWhoCanThenReadTheOrg() {
    var org = TestHelpers.managedOrg();
    var lloyd = TestHelpers.newAccount();

    TestHelpers.authed(org.token())
        .contentType(ContentType.JSON)
        .body(new AddMemberRequest(lloyd.username(), List.of("system:viewer")))
        .when()
        .post("/api/v1/orgs/" + org.id() + "/members")
        .then()
        .statusCode(201)
        .body("username", equalTo(lloyd.username()))
        .body("membershipId", notNullValue())
        .body("policyIds", contains("system:viewer"));

    var lloydActive = TestHelpers.switchOrgAs(lloyd.token(), org.id());
    TestHelpers.authed(lloydActive.token())
        .when()
        .get("/api/v1/orgs/" + org.id())
        .then()
        .statusCode(200)
        .body("id", equalTo(org.id().toString()));
  }

  @Test
  void managerAddsAMemberWithMultiplePolicies() {
    var org = TestHelpers.managedOrg();
    var lloyd = TestHelpers.newAccount();

    TestHelpers.authed(org.token())
        .contentType(ContentType.JSON)
        .body(new AddMemberRequest(lloyd.username(), List.of("system:viewer", "system:admin")))
        .when()
        .post("/api/v1/orgs/" + org.id() + "/members")
        .then()
        .statusCode(201)
        .body("policyIds", containsInAnyOrder("system:viewer", "system:admin"));
  }

  @Test
  void addingAsANonManagerIsForbidden() {
    var org = TestHelpers.managedOrg();
    var viewer = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(org, viewer.username(), new SystemPolicyId("system:viewer"));
    var viewerActive = TestHelpers.switchOrgAs(viewer.token(), org.id());
    var dave = TestHelpers.newAccount();

    TestHelpers.authed(viewerActive.token())
        .contentType(ContentType.JSON)
        .body(new AddMemberRequest(dave.username(), List.of("system:viewer")))
        .when()
        .post("/api/v1/orgs/" + org.id() + "/members")
        .then()
        .statusCode(403)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void managerReplacesAMembersPoliciesAndTheRosterReflectsIt() {
    var org = TestHelpers.managedOrg();
    var lloyd = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(org, lloyd.username(), new SystemPolicyId("system:viewer"));

    TestHelpers.authed(org.token())
        .contentType(ContentType.JSON)
        .body(new PolicyIdsRequest(List.of("system:admin")))
        .when()
        .put("/api/v1/orgs/" + org.id() + "/members/" + lloyd.username() + "/policies")
        .then()
        .statusCode(200)
        .body("username", equalTo(lloyd.username()))
        .body("policyIds", contains("system:admin"));

    TestHelpers.authed(org.token())
        .when()
        .get("/api/v1/orgs/" + org.id() + "/members")
        .then()
        .statusCode(200)
        .body(rosterPolicies(lloyd.username()), contains("system:admin"));
  }

  @Test
  void replacingWithAnEmptySetStripsEveryRole() {
    var org = TestHelpers.managedOrg();
    var lloyd = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(org, lloyd.username(), new SystemPolicyId("system:viewer"));

    TestHelpers.authed(org.token())
        .contentType(ContentType.JSON)
        .body(new PolicyIdsRequest(List.of()))
        .when()
        .put("/api/v1/orgs/" + org.id() + "/members/" + lloyd.username() + "/policies")
        .then()
        .statusCode(200)
        .body("policyIds.size()", equalTo(0));

    // With no policies lloyd loses org-read, so the org is no longer visible to them.
    var lloydActive = TestHelpers.switchOrgAs(lloyd.token(), org.id());
    TestHelpers.authed(lloydActive.token())
        .when()
        .get("/api/v1/orgs/" + org.id())
        .then()
        .statusCode(404);
  }

  @Test
  void replacingWithABadIdFailsAtomicallyAndLeavesTheSetUnchanged() {
    var org = TestHelpers.managedOrg();
    var lloyd = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(org, lloyd.username(), new SystemPolicyId("system:viewer"));

    TestHelpers.authed(org.token())
        .contentType(ContentType.JSON)
        .body(new PolicyIdsRequest(List.of("system:admin", "system:bogus")))
        .when()
        .put("/api/v1/orgs/" + org.id() + "/members/" + lloyd.username() + "/policies")
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("message", equalTo("invalid policies"))
        .body("errors.policyId", hasItem("system:bogus"));

    // Nothing changed: the admin from the rejected request was not applied.
    TestHelpers.authed(org.token())
        .when()
        .get("/api/v1/orgs/" + org.id() + "/members")
        .then()
        .statusCode(200)
        .body(rosterPolicies(lloyd.username()), contains("system:viewer"));
  }

  @Test
  void settingPoliciesAsANonManagerIsForbidden() {
    var org = TestHelpers.managedOrg();
    var viewer = TestHelpers.newAccount();
    var joe = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(org, viewer.username(), new SystemPolicyId("system:viewer"));
    TestHelpers.addMemberToOrg(org, joe.username(), new SystemPolicyId("system:viewer"));
    var viewerActive = TestHelpers.switchOrgAs(viewer.token(), org.id());

    TestHelpers.authed(viewerActive.token())
        .contentType(ContentType.JSON)
        .body(new PolicyIdsRequest(List.of("system:admin")))
        .when()
        .put("/api/v1/orgs/" + org.id() + "/members/" + joe.username() + "/policies")
        .then()
        .statusCode(403)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void settingPoliciesWhileActiveElsewhereReturns404() {
    var owner = TestHelpers.newAccount();
    var orgB = TestHelpers.createOrgAs(owner.token(), "beachape");
    var ownerB = TestHelpers.switchOrgAs(owner.token(), orgB);
    var lloyd = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(ownerB, lloyd.username(), new SystemPolicyId("system:viewer"));

    var orgA = TestHelpers.createOrgAs(owner.token(), "acme");
    var ownerA = TestHelpers.switchOrgAs(owner.token(), orgA);

    TestHelpers.authed(ownerA.token())
        .contentType(ContentType.JSON)
        .body(new PolicyIdsRequest(List.of("system:admin")))
        .when()
        .put("/api/v1/orgs/" + orgB + "/members/" + lloyd.username() + "/policies")
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void settingPoliciesForAnUnknownMemberReturns404Json() {
    var org = TestHelpers.managedOrg();

    TestHelpers.authed(org.token())
        .contentType(ContentType.JSON)
        .body(new PolicyIdsRequest(List.of("system:admin")))
        .when()
        .put(
            "/api/v1/orgs/"
                + org.id()
                + "/members/ghost"
                + randomUUID().toString().replace("-", "")
                + "/policies")
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void settingPoliciesWithoutAuthReturns401Json() {
    given()
        .contentType(ContentType.JSON)
        .body(new PolicyIdsRequest(List.of("system:admin")))
        .when()
        .put("/api/v1/orgs/" + randomUUID() + "/members/lloyd/policies")
        .then()
        .statusCode(401)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void rosterListsMembersEachWithTheirOwnPolicies() {
    var org = TestHelpers.managedOrg();
    var lloyd = TestHelpers.newAccount();
    var joe = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(org, lloyd.username(), new SystemPolicyId("system:viewer"));
    TestHelpers.addMemberToOrg(
        org,
        joe.username(),
        List.of(new SystemPolicyId("system:admin"), new SystemPolicyId("system:manager")));

    TestHelpers.authed(org.token())
        .when()
        .get("/api/v1/orgs/" + org.id() + "/members")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("values.username", hasItems(lloyd.username(), joe.username()))
        .body("values.membershipId", everyItem(notNullValue()))
        .body(rosterPolicies(lloyd.username()), contains("system:viewer"))
        .body(rosterPolicies(joe.username()), containsInAnyOrder("system:admin", "system:manager"));
  }

  @Test
  void rosterIsNotVisibleToANonMember() {
    var org = TestHelpers.managedOrg();
    var outsider = TestHelpers.newAccount();

    TestHelpers.authed(outsider.token())
        .when()
        .get("/api/v1/orgs/" + org.id() + "/members")
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void rosterWithoutAuthReturns401Json() {
    given()
        .when()
        .get("/api/v1/orgs/" + randomUUID() + "/members")
        .then()
        .statusCode(401)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void managerKicksAMemberWhoThenLosesAccess() {
    var org = TestHelpers.managedOrg();
    var lloyd = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(org, lloyd.username(), new SystemPolicyId("system:viewer"));

    TestHelpers.authed(org.token())
        .when()
        .delete("/api/v1/orgs/" + org.id() + "/members/" + lloyd.username())
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON);

    // No longer a member, so cannot switch into acme.
    TestHelpers.authed(lloyd.token())
        .contentType(ContentType.JSON)
        .body(new SwitchOrgRequest(org.id().value().toString()))
        .when()
        .post("/api/v1/sessions/switch-org")
        .then()
        .statusCode(404);
  }

  @Test
  void aMemberCanLeaveAndLosesAccess() {
    var org = TestHelpers.managedOrg();
    var lloyd = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(org, lloyd.username(), new SystemPolicyId("system:viewer"));
    var lloydActive = TestHelpers.switchOrgAs(lloyd.token(), org.id());

    TestHelpers.authed(lloydActive.token())
        .when()
        .delete("/api/v1/orgs/" + org.id() + "/membership")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON);

    TestHelpers.authed(lloyd.token())
        .contentType(ContentType.JSON)
        .body(new SwitchOrgRequest(org.id().value().toString()))
        .when()
        .post("/api/v1/sessions/switch-org")
        .then()
        .statusCode(404);
  }

  @Test
  void aViewerCanSelfLeaveButCannotKickAnother() {
    var org = TestHelpers.managedOrg();
    var lloyd = TestHelpers.newAccount();
    var joe = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(org, lloyd.username(), new SystemPolicyId("system:viewer"));
    TestHelpers.addMemberToOrg(org, joe.username(), new SystemPolicyId("system:viewer"));
    var lloydActive = TestHelpers.switchOrgAs(lloyd.token(), org.id());

    // A viewer holds membership:read but not membership:delete, so kicking a real member is an
    // 403 (they can see the roster, just not remove anyone)
    TestHelpers.authed(lloydActive.token())
        .when()
        .delete("/api/v1/orgs/" + org.id() + "/members/" + joe.username())
        .then()
        .statusCode(403)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());

    // But self_membership:delete lets them leave.
    TestHelpers.authed(lloydActive.token())
        .when()
        .delete("/api/v1/orgs/" + org.id() + "/membership")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON);
  }

  @Test
  void cannotManageMembersOfAnOrgWhileActiveElsewhere() {
    var lloyd = TestHelpers.newAccount();
    var orgB = TestHelpers.createOrgAs(lloyd.token(), "beachape");
    var lloydActiveB = TestHelpers.switchOrgAs(lloyd.token(), orgB);

    var alice = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(
        lloydActiveB,
        alice.username(),
        new SystemPolicyId("system:manager")); // even a manager of B...
    var orgA = TestHelpers.createOrgAs(alice.token(), "acme");
    var aliceActiveA = TestHelpers.switchOrgAs(alice.token(), orgA); // ...but active in A

    var joe = TestHelpers.newAccount();
    TestHelpers.authed(aliceActiveA.token())
        .contentType(ContentType.JSON)
        .body(new AddMemberRequest(joe.username(), List.of("system:viewer")))
        .when()
        .post("/api/v1/orgs/" + orgB + "/members")
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void addingAnUnknownUserReturns404Json() {
    var org = TestHelpers.managedOrg();

    TestHelpers.authed(org.token())
        .contentType(ContentType.JSON)
        .body(
            new AddMemberRequest(
                "ghost" + randomUUID().toString().replace("-", ""), List.of("system:viewer")))
        .when()
        .post("/api/v1/orgs/" + org.id().value() + "/members")
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void addingAnExistingMemberReturns409Json() {
    var org = TestHelpers.managedOrg();
    var lloyd = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(org, lloyd.username(), new SystemPolicyId("system:viewer"));

    TestHelpers.authed(org.token())
        .contentType(ContentType.JSON)
        .body(new AddMemberRequest(lloyd.username(), List.of("system:admin")))
        .when()
        .post("/api/v1/orgs/" + org.id().value() + "/members")
        .then()
        .statusCode(409)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void addingWithAnUnknownPolicyReturns400WithEveryFailure() {
    var org = TestHelpers.managedOrg();
    var lloyd = TestHelpers.newAccount();

    TestHelpers.authed(org.token())
        .contentType(ContentType.JSON)
        .body(new AddMemberRequest(lloyd.username(), List.of("system:superadmin")))
        .when()
        .post("/api/v1/orgs/" + org.id() + "/members")
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("message", equalTo("invalid policies"))
        .body("errors.policyId", hasItem("system:superadmin"));
  }

  @Test
  void addingWithoutAuthReturns401Json() {
    given()
        .contentType(ContentType.JSON)
        .body(new AddMemberRequest("lloyd", List.of("system:viewer")))
        .when()
        .post("/api/v1/orgs/" + randomUUID() + "/members")
        .then()
        .statusCode(401)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void kickingANonMemberReturns404Json() {
    var org = TestHelpers.managedOrg();

    TestHelpers.authed(org.token())
        .when()
        .delete(
            "/api/v1/orgs/"
                + org.id()
                + "/members/ghost"
                + randomUUID().toString().replace("-", ""))
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  private static String rosterPolicies(String username) {
    return "values.find { it.username == '" + username + "' }.policyIds";
  }
}
