package com.beachape.aminam.integration.authz;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.beachape.aminam.app.routes.v1.orgs.models.PolicyIdsRequest;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyAction;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyEffect;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyResourcePattern;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyResourceType;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyStatement;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyVerb;
import com.beachape.aminam.domain.authz.services.SystemPolicies;
import com.beachape.aminam.integration.utils.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

// End-to-end stories: RBAC and org confinement through CEL conditions to logout revocation, and a
// creator policy confining each member to the databases they made.
@QuarkusTest
final class UserScenarioTest {

  @Test
  void viewerRbacDenyOverrideCelConditionsAndTokenRevocation() {
    var lloyd = TestHelpers.newAccount();
    var bob = TestHelpers.newAccount();

    var beachapeId = TestHelpers.createOrgAs(lloyd.token(), "beachape");
    var lloydSession = TestHelpers.switchOrgAs(lloyd.token(), beachapeId);
    TestHelpers.addMemberToOrg(lloydSession, bob.username(), List.of(SystemPolicies.VIEWER));

    // Bob's token is still anchored to his personal org, so beachape is invisible from there.
    TestHelpers.listDatabasesAs(bob.token(), beachapeId)
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());

    var bobSession = TestHelpers.switchOrgAs(bob.token(), beachapeId);

    var alpha = TestHelpers.createDatabaseIn(lloydSession, "alpha");
    TestHelpers.readDatabaseAs(bobSession.token(), alpha)
        .statusCode(200)
        .body("editable", equalTo(false));
    TestHelpers.deleteDatabaseAs(bobSession.token(), alpha)
        .statusCode(403)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());

    // Privilege escalation: a viewer cannot attach system:manager to itself.
    TestHelpers.authed(bobSession.token())
        .contentType(ContentType.JSON)
        .body(new PolicyIdsRequest(List.of(SystemPolicies.MANAGER.asText())))
        .when()
        .put("/api/v1/orgs/" + beachapeId.value() + "/members/" + bob.username() + "/policies")
        .then()
        .statusCode(403)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());

    // A specific DENY on gamma overrides the wildcard ALLOW for update and delete.
    var beta = TestHelpers.createDatabaseIn(lloydSession, "beta");
    var gamma = TestHelpers.createDatabaseIn(lloydSession, "gamma");
    var denyOverride =
        TestHelpers.createPolicyIn(
            lloydSession,
            "edit-and-delete-all-except-gamma",
            List.of(
                statement(
                    PolicyEffect.DENY,
                    List.of(PolicyVerb.UPDATE, PolicyVerb.DELETE),
                    gamma.value(),
                    null),
                statement(
                    PolicyEffect.ALLOW,
                    List.of(PolicyVerb.UPDATE, PolicyVerb.DELETE),
                    null,
                    null)));
    TestHelpers.setMemberPoliciesAs(
        lloydSession, bob.username(), List.of(SystemPolicies.VIEWER, denyOverride));

    // The org holds exactly these three, so pin membership and order: alpha and beta editable,
    // gamma protected by the DENY.
    TestHelpers.listDatabasesAs(bobSession.token(), beachapeId)
        .statusCode(200)
        .body("values.name", contains("alpha", "beta", "gamma"))
        .body("values.editable", contains(true, true, false));

    TestHelpers.editDatabaseAs(bobSession.token(), alpha, "alpha-edited").statusCode(200);
    TestHelpers.editDatabaseAs(bobSession.token(), beta, "beta-edited").statusCode(200);
    TestHelpers.editDatabaseAs(bobSession.token(), gamma, "gamma-edited")
        .statusCode(403)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());

    TestHelpers.deleteDatabaseAs(bobSession.token(), alpha).statusCode(200);
    TestHelpers.deleteDatabaseAs(bobSession.token(), beta).statusCode(200);
    TestHelpers.deleteDatabaseAs(bobSession.token(), gamma)
        .statusCode(403)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());

    // CEL: an update grant gated by a name prefix, plus an explicit DENY on one specific database.
    var reportOpen = TestHelpers.createDatabaseIn(lloydSession, "report-open");
    var reportLocked = TestHelpers.createDatabaseIn(lloydSession, "report-locked");
    var ledger = TestHelpers.createDatabaseIn(lloydSession, "ledger");
    var reportEditors =
        TestHelpers.createPolicyIn(
            lloydSession,
            "report-editors",
            List.of(
                statement(
                    PolicyEffect.ALLOW,
                    List.of(PolicyVerb.UPDATE),
                    null,
                    "resource.name.startsWith('report-')"),
                statement(
                    PolicyEffect.DENY, List.of(PolicyVerb.UPDATE), reportLocked.value(), null)));
    TestHelpers.setMemberPoliciesAs(
        lloydSession, bob.username(), List.of(SystemPolicies.VIEWER, reportEditors));

    // Condition true, no DENY: editable, and the edit lands. The editable flag reflects the CEL
    // condition because it is the same update decision the edit runs.
    TestHelpers.readDatabaseAs(bobSession.token(), reportOpen)
        .statusCode(200)
        .body("editable", equalTo(true));
    TestHelpers.editDatabaseAs(bobSession.token(), reportOpen, "report-open-edited")
        .statusCode(200);

    // Condition true, but an explicit DENY overrides the satisfied ALLOW.
    TestHelpers.readDatabaseAs(bobSession.token(), reportLocked)
        .statusCode(200)
        .body("editable", equalTo(false));
    TestHelpers.editDatabaseAs(bobSession.token(), reportLocked, "report-locked-edited")
        .statusCode(403)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());

    // Condition false: the viewer read still applies (200), but there is no update grant.
    TestHelpers.readDatabaseAs(bobSession.token(), ledger)
        .statusCode(200)
        .body("editable", equalTo(false));
    TestHelpers.editDatabaseAs(bobSession.token(), ledger, "ledger-edited")
        .statusCode(403)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());

    // Logout blocklists the session server-side even though the JWT is still structurally valid.
    TestHelpers.authed(bobSession.token())
        .when()
        .post("/api/v1/logout")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON);

    TestHelpers.authed(bobSession.token())
        .when()
        .get("/api/v1/me")
        .then()
        .statusCode(401)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void aCreatorPolicyConfinesEachMemberToTheDatabasesTheyCreated() {
    var lloyd = TestHelpers.newAccount();
    var bob = TestHelpers.newAccount();

    var beachapeId = TestHelpers.createOrgAs(lloyd.token(), "beachape");
    var lloydSession = TestHelpers.switchOrgAs(lloyd.token(), beachapeId);

    // Create stays unconditioned because a database being created has no facts yet: an ownership
    // condition could never hold on it. Ownership gates read, update and delete instead.
    var manageOwnDatabases =
        TestHelpers.createPolicyIn(
            lloydSession,
            "own-databases",
            List.of(
                orgRead(),
                statement(PolicyEffect.ALLOW, List.of(PolicyVerb.CREATE), null, null),
                statement(
                    PolicyEffect.ALLOW,
                    List.of(PolicyVerb.READ, PolicyVerb.UPDATE, PolicyVerb.DELETE),
                    null,
                    "resource.created_by == principal.id")));

    // Bob holds the creator policy alone. No system:viewer: its unconditional database:read would
    // let him see lloyd's databases and defeat the point.
    TestHelpers.addMemberToOrg(lloydSession, bob.username(), List.of(manageOwnDatabases));
    var bobSession = TestHelpers.switchOrgAs(bob.token(), beachapeId);

    var ledger = TestHelpers.createDatabaseIn(lloydSession, "ledger");
    var bobNotes = TestHelpers.createDatabaseIn(bobSession, "bob-notes");

    // His own: readable, editable, and the edit lands.
    TestHelpers.readDatabaseAs(bobSession.token(), bobNotes)
        .statusCode(200)
        .body("createdBy", notNullValue())
        .body("editable", equalTo(true));
    TestHelpers.editDatabaseAs(bobSession.token(), bobNotes, "bob-notes-edited").statusCode(200);

    // Lloyd's: 404, not 403 since read is denied implicitly
    TestHelpers.readDatabaseAs(bobSession.token(), ledger).statusCode(404);
    TestHelpers.editDatabaseAs(bobSession.token(), ledger, "ledger-edited").statusCode(404);
    TestHelpers.deleteDatabaseAs(bobSession.token(), ledger).statusCode(404);

    // bob's view of the org holds only what he made.
    TestHelpers.listDatabasesAs(bobSession.token(), beachapeId)
        .statusCode(200)
        .body("values.name", contains("bob-notes-edited"));
    TestHelpers.listDatabasesAs(lloydSession.token(), beachapeId)
        .statusCode(200)
        .body("values.name", contains("ledger", "bob-notes-edited"));

    // A rename does not transfer authorship, so bob still owns what he renamed.
    TestHelpers.deleteDatabaseAs(bobSession.token(), bobNotes).statusCode(200);
  }

  private static PolicyStatement orgRead() {
    return new PolicyStatement(
        PolicyEffect.ALLOW,
        null,
        List.of(new PolicyAction(PolicyResourceType.ORG, PolicyVerb.READ)),
        List.of(new PolicyResourcePattern(PolicyResourceType.ORG, null)),
        null);
  }

  private static PolicyStatement statement(
      PolicyEffect effect,
      List<PolicyVerb> verbs,
      @Nullable UUID resourceId,
      @Nullable String condition) {
    return new PolicyStatement(
        effect,
        null,
        verbs.stream().map(verb -> new PolicyAction(PolicyResourceType.DATABASE, verb)).toList(),
        List.of(new PolicyResourcePattern(PolicyResourceType.DATABASE, resourceId)),
        condition);
  }
}
