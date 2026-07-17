package com.beachape.aminam.integration.authz;

import static java.util.UUID.randomUUID;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;

import com.beachape.aminam.app.routes.v1.orgs.models.PolicyIdsRequest;
import com.beachape.aminam.domain.authz.services.SystemPolicies;
import com.beachape.aminam.integration.utils.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.List;
import org.junit.jupiter.api.Test;

// The org/membership decision matrix at the HTTP boundary.
// Sibling of DatabaseAccessAuthzTest.
@QuarkusTest
final class OrgAccessAuthzTest {

  @Test
  void creatorReadsOwnOrg() {
    var org = TestHelpers.managedOrg();

    TestHelpers.authed(org.token())
        .when()
        .get("/api/v1/orgs/" + org.id().value())
        .then()
        .statusCode(200)
        .body("id", equalTo(org.id().value().toString()))
        .body("name", equalTo("acme"));
  }

  @Test
  void memberWithViewerPolicyCanReadTheOrg() {
    var org = TestHelpers.managedOrg();
    var viewer = TestHelpers.createNewUserAsMemberIn(org, List.of(SystemPolicies.VIEWER));

    TestHelpers.authed(viewer)
        .when()
        .get("/api/v1/orgs/" + org.id().value())
        .then()
        .statusCode(200)
        .body("id", equalTo(org.id().value().toString()))
        .body("name", equalTo("acme"));
  }

  @Test
  void memberWithNoPolicyIsDenied404() {
    var org = TestHelpers.managedOrg();
    var member = TestHelpers.createNewUserAsMemberIn(org, List.of());

    TestHelpers.authed(member)
        .when()
        .get("/api/v1/orgs/" + org.id().value())
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void memberActiveElsewhereIsDenied404() {
    var account = TestHelpers.newAccount();
    var id = TestHelpers.createOrgAs(account.token(), "acme");
    // Active org is still the personal org, so acme is cross-org and invisible until switched into.

    TestHelpers.authed(account.token())
        .when()
        .get("/api/v1/orgs/" + id.value())
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void nonMemberIsDenied404() {
    var owner = TestHelpers.newAccount();
    var id = TestHelpers.createOrgAs(owner.token(), "acme");
    var other = TestHelpers.newAccount();

    TestHelpers.authed(other.token())
        .when()
        .get("/api/v1/orgs/" + id.value())
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void unknownOrgIsDenied404() {
    var account = TestHelpers.newAccount();

    TestHelpers.authed(account.token())
        .when()
        .get("/api/v1/orgs/" + randomUUID())
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void crossOrgMembershipReadIsDenied() {
    var org = TestHelpers.managedOrg();
    var member = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(org, member.username(), List.of(SystemPolicies.VIEWER));

    // Cross-org: the outsider's session is in another org, so the regime denies reading acme's
    // roster (they even hold membership:read in their own org). Seen via the kick gate as 404.
    var outsider = TestHelpers.newAccount();
    TestHelpers.authed(outsider.token())
        .when()
        .delete("/api/v1/orgs/" + org.id().value() + "/members/" + member.username())
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void viewerCanReadTheRoster() {
    var owner = TestHelpers.newAccount();
    var orgId = TestHelpers.createOrgAs(owner.token(), "acme");
    var ownerActive = TestHelpers.switchOrgAs(owner.token(), orgId);
    var viewer = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(ownerActive, viewer.username(), List.of(SystemPolicies.VIEWER));
    var viewerActive = TestHelpers.switchOrgAs(viewer.token(), orgId);

    // Reading the roster is low-privilege: viewer holds membership:read, so sees every member.
    TestHelpers.authed(viewerActive.token())
        .when()
        .get("/api/v1/orgs/" + orgId.value() + "/members")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("values.username", hasItems(owner.username(), viewer.username()));
  }

  @Test
  void roleLessMemberCannotReadTheRoster() {
    var org = TestHelpers.managedOrg();
    var member = TestHelpers.createNewUserAsMemberIn(org, List.of());

    // Membership is not org:read: a role-less member cannot see the org, so the roster is 404.
    TestHelpers.authed(member)
        .when()
        .get("/api/v1/orgs/" + org.id().value() + "/members")
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void memberActiveElsewhereCannotReadTheRoster() {
    var account = TestHelpers.newAccount();
    var orgId = TestHelpers.createOrgAs(account.token(), "acme");
    // Active org is still the personal org, so acme's roster is cross-org and invisible.

    TestHelpers.authed(account.token())
        .when()
        .get("/api/v1/orgs/" + orgId.value() + "/members")
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void viewerCannotSetMemberPolicies() {
    var org = TestHelpers.managedOrg();
    var actor = TestHelpers.createNewUserAsMemberIn(org, List.of(SystemPolicies.VIEWER));
    var target = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(org, target.username(), List.of(SystemPolicies.VIEWER));

    TestHelpers.authed(actor)
        .contentType(ContentType.JSON)
        .body(new PolicyIdsRequest(List.of("system:admin")))
        .when()
        .put("/api/v1/orgs/" + org.id().value() + "/members/" + target.username() + "/policies")
        .then()
        .statusCode(403)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void adminCannotSetMemberPolicies() {
    var org = TestHelpers.managedOrg();
    var actor = TestHelpers.createNewUserAsMemberIn(org, List.of(SystemPolicies.ADMIN));
    var target = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(org, target.username(), List.of(SystemPolicies.VIEWER));

    // Admin is data-power-only: it holds membership:read (target visible) but no attach/detach, so
    // managing a member's policies is a 403, not a 404.
    TestHelpers.authed(actor)
        .contentType(ContentType.JSON)
        .body(new PolicyIdsRequest(List.of("system:admin")))
        .when()
        .put("/api/v1/orgs/" + org.id().value() + "/members/" + target.username() + "/policies")
        .then()
        .statusCode(403)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void roleLessActorCannotSeeAMemberToSetPolicies() {
    var org = TestHelpers.managedOrg();
    var actor =
        TestHelpers.createNewUserAsMemberIn(org, List.of()); // role-less: no membership:read
    var target = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(org, target.username(), List.of(SystemPolicies.VIEWER));

    // READ-first: a role-less actor cannot read the target membership, so it is 404 (not a 403).
    TestHelpers.authed(actor)
        .contentType(ContentType.JSON)
        .body(new PolicyIdsRequest(List.of("system:admin")))
        .when()
        .put("/api/v1/orgs/" + org.id().value() + "/members/" + target.username() + "/policies")
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  // <-- multi -->

  @Test
  void viewerPlusManagerCanSetMemberPolicies() {
    var org = TestHelpers.managedOrg();
    var actor =
        TestHelpers.createNewUserAsMemberIn(
            org, List.of(SystemPolicies.VIEWER, SystemPolicies.MANAGER));
    var target = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(org, target.username(), List.of(SystemPolicies.VIEWER));

    // Manager grants membership:attach/detach, so the union can re-role a member (viewer alone
    // 403s).
    TestHelpers.authed(actor)
        .contentType(ContentType.JSON)
        .body(new PolicyIdsRequest(List.of("system:admin")))
        .when()
        .put("/api/v1/orgs/" + org.id().value() + "/members/" + target.username() + "/policies")
        .then()
        .statusCode(200)
        .body("policyIds", hasItems("system:admin"));
  }

  @Test
  void viewerPlusAdminCannotSetMemberPolicies() {
    var org = TestHelpers.managedOrg();
    var actor =
        TestHelpers.createNewUserAsMemberIn(
            org, List.of(SystemPolicies.VIEWER, SystemPolicies.ADMIN));
    var target = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(org, target.username(), List.of(SystemPolicies.VIEWER));

    // Neither viewer nor admin holds membership:attach/detach, so the union does not either
    TestHelpers.authed(actor)
        .contentType(ContentType.JSON)
        .body(new PolicyIdsRequest(List.of("system:admin")))
        .when()
        .put("/api/v1/orgs/" + org.id().value() + "/members/" + target.username() + "/policies")
        .then()
        .statusCode(403)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  // <-- /multi -->

}
