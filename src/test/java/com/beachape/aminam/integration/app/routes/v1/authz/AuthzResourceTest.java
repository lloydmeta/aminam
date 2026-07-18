package com.beachape.aminam.integration.app.routes.v1.authz;

import static io.restassured.RestAssured.given;
import static java.util.UUID.randomUUID;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.beachape.aminam.app.routes.v1.authz.models.AuthzResourceType;
import com.beachape.aminam.app.routes.v1.authz.models.AuthzVerb;
import com.beachape.aminam.app.routes.v1.authz.models.CheckItem;
import com.beachape.aminam.app.routes.v1.authz.models.CheckRequest;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyAction;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyEffect;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyResourcePattern;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyResourceType;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyStatement;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyVerb;
import com.beachape.aminam.domain.authz.models.PolicyId;
import com.beachape.aminam.domain.authz.services.SystemPolicies;
import com.beachape.aminam.domain.databases.models.DatabaseId;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import com.beachape.aminam.integration.utils.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/// Endpoint mechanics and basic decisions for the PDP `/authz/_check`; the decision matrix lives in
/// integration/authz/.
@QuarkusTest
final class AuthzResourceTest {

  @Test
  void managerGetsAllOkForEveryAllowedCheck() {
    var beachape = TestHelpers.managedOrg();
    var db = TestHelpers.createDatabaseIn(beachape, "metrics");

    TestHelpers.authed(beachape.token())
        .contentType(ContentType.JSON)
        .body(new CheckRequest(List.of(read(db.value()), update(db.value()))))
        .when()
        .post("/api/v1/authz/_check")
        .then()
        .statusCode(200)
        .body("allOk", equalTo(true))
        .body("results.size()", equalTo(2))
        .body("results[0].ok", equalTo(true))
        .body("results[1].ok", equalTo(true));
  }

  @Test
  void viewerReadsButCannotUpdateGivingMixedResults() {
    var beachape = TestHelpers.managedOrg();
    var db = TestHelpers.createDatabaseIn(beachape, "metrics");
    var viewer = TestHelpers.createNewUserAsMemberIn(beachape, List.of(SystemPolicies.VIEWER));

    TestHelpers.authed(viewer)
        .contentType(ContentType.JSON)
        .body(new CheckRequest(List.of(read(db.value()), update(db.value()))))
        .when()
        .post("/api/v1/authz/_check")
        .then()
        .statusCode(200)
        .body("allOk", equalTo(false))
        .body("results[0].ok", equalTo(true))
        .body("results[1].ok", equalTo(false))
        .body("results[1].reason", notNullValue());
  }

  @Test
  void unknownResourceIsDeniedInBandWithoutLeaking() {
    var beachape = TestHelpers.managedOrg();

    TestHelpers.authed(beachape.token())
        .contentType(ContentType.JSON)
        .body(new CheckRequest(List.of(read(randomUUID()))))
        .when()
        .post("/api/v1/authz/_check")
        .then()
        .statusCode(200)
        .body("allOk", equalTo(false))
        .body("results[0].ok", equalTo(false))
        .body("results[0].reason", equalTo("resource not found"));
  }

  @Test
  void managerMayCreateADatabaseInTheirOwnOrg() {
    var beachape = TestHelpers.managedOrg();

    TestHelpers.authed(beachape.token())
        .contentType(ContentType.JSON)
        .body(
            new CheckRequest(
                List.of(
                    new CheckItem.ToCreate(
                        AuthzResourceType.DATABASE, AuthzVerb.CREATE, beachape.id().value()))))
        .when()
        .post("/api/v1/authz/_check")
        .then()
        .statusCode(200)
        .body("allOk", equalTo(true))
        .body("results[0].ok", equalTo(true));
  }

  @Test
  void crossOrgCheckIsDeniedBeforeAnyShare() {
    var crossOrg = crossOrgSetup();

    TestHelpers.authed(crossOrg.beachape().token())
        .contentType(ContentType.JSON)
        .body(new CheckRequest(List.of(read(crossOrg.acmeDb().value()))))
        .when()
        .post("/api/v1/authz/_check")
        .then()
        .statusCode(200)
        .body("allOk", equalTo(false))
        .body("results[0].ok", equalTo(false));
  }

  @Test
  void crossOrgReadIsAllowedAfterBilateralConsentButUpdateStaysDenied() {
    var crossOrg = crossOrgSetup();
    shareRead(crossOrg.acme(), crossOrg.acmeDb(), crossOrg.lloydMembership());

    TestHelpers.authed(crossOrg.beachape().token())
        .contentType(ContentType.JSON)
        .body(
            new CheckRequest(
                List.of(read(crossOrg.acmeDb().value()), update(crossOrg.acmeDb().value()))))
        .when()
        .post("/api/v1/authz/_check")
        .then()
        .statusCode(200)
        .body("allOk", equalTo(false))
        .body("results[0].ok", equalTo(true))
        .body("results[1].ok", equalTo(false));
  }

  @Test
  void celNamePrefixConditionGatesEachCheckInBand() {
    var beachape = TestHelpers.managedOrg();
    var policyId =
        conditionalUpdatePolicy(beachape, "report-editors", "resource.name.startsWith('report-')");
    var reportDb = TestHelpers.createDatabaseIn(beachape, "report-sales");
    var ledgerDb = TestHelpers.createDatabaseIn(beachape, "ledger");
    var lloyd =
        TestHelpers.createNewUserAsMemberIn(beachape, List.of(SystemPolicies.VIEWER, policyId));

    TestHelpers.authed(lloyd)
        .contentType(ContentType.JSON)
        .body(new CheckRequest(List.of(update(reportDb.value()), update(ledgerDb.value()))))
        .when()
        .post("/api/v1/authz/_check")
        .then()
        .statusCode(200)
        .body("allOk", equalTo(false))
        .body("results[0].ok", equalTo(true))
        .body("results[1].ok", equalTo(false));
  }

  @Test
  void celServerResolvedOrgConditionAllowsAnInOrgUpdate() {
    var beachape = TestHelpers.managedOrg();
    var policyId =
        conditionalUpdatePolicy(
            beachape, "own-org-editors", "resource.org_id == principal.active_org");
    var db = TestHelpers.createDatabaseIn(beachape, "anything");
    var lloyd =
        TestHelpers.createNewUserAsMemberIn(beachape, List.of(SystemPolicies.VIEWER, policyId));

    TestHelpers.authed(lloyd)
        .contentType(ContentType.JSON)
        .body(new CheckRequest(List.of(update(db.value()))))
        .when()
        .post("/api/v1/authz/_check")
        .then()
        .statusCode(200)
        .body("allOk", equalTo(true))
        .body("results[0].ok", equalTo(true));
  }

  @Test
  void emptyChecksReturns400() {
    var beachape = TestHelpers.managedOrg();

    TestHelpers.authed(beachape.token())
        .contentType(ContentType.JSON)
        .body(new CheckRequest(List.of()))
        .when()
        .post("/api/v1/authz/_check")
        .then()
        .statusCode(400);
  }

  @Test
  void anUnknownVerbIsRejectedWith400() {
    var beachape = TestHelpers.managedOrg();
    var check =
        Map.of(
            "kind", "EXISTING", "type", "DATABASE", "verb", "FLY", "id", randomUUID().toString());

    TestHelpers.authed(beachape.token())
        .contentType(ContentType.JSON)
        .body(Map.of("checks", List.of(check)))
        .when()
        .post("/api/v1/authz/_check")
        .then()
        .statusCode(400);
  }

  @Test
  void anUnknownKindIsRejectedWith400() {
    var beachape = TestHelpers.managedOrg();
    var check =
        Map.of("kind", "BOGUS", "type", "DATABASE", "verb", "READ", "id", randomUUID().toString());

    TestHelpers.authed(beachape.token())
        .contentType(ContentType.JSON)
        .body(Map.of("checks", List.of(check)))
        .when()
        .post("/api/v1/authz/_check")
        .then()
        .statusCode(400);
  }

  @Test
  void anExistingCheckMissingItsIdIsRejectedWith400() {
    var beachape = TestHelpers.managedOrg();
    var check = Map.of("kind", "EXISTING", "type", "DATABASE", "verb", "READ");

    TestHelpers.authed(beachape.token())
        .contentType(ContentType.JSON)
        .body(Map.of("checks", List.of(check)))
        .when()
        .post("/api/v1/authz/_check")
        .then()
        .statusCode(400);
  }

  @Test
  void checkingWithoutAuthReturns401Json() {
    given()
        .contentType(ContentType.JSON)
        .body(new CheckRequest(List.of(read(randomUUID()))))
        .when()
        .post("/api/v1/authz/_check")
        .then()
        .statusCode(401)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  private static CheckItem read(UUID id) {
    return new CheckItem.Existing(AuthzResourceType.DATABASE, AuthzVerb.READ, id);
  }

  private static CheckItem update(UUID id) {
    return new CheckItem.Existing(AuthzResourceType.DATABASE, AuthzVerb.UPDATE, id);
  }

  private static CrossOrg crossOrgSetup() {
    var acme = TestHelpers.managedOrg("acme");
    var acmeDb = TestHelpers.createDatabaseIn(acme, "metrics");
    var lloyd = TestHelpers.newAccount();
    var beachape =
        TestHelpers.switchOrgAs(lloyd.token(), TestHelpers.createOrgAs(lloyd.token(), "beachape"));
    var lloydMembership = TestHelpers.getMembershipIdByUsernameAs(beachape, lloyd.username());
    return new CrossOrg(acme, acmeDb, beachape, lloydMembership);
  }

  private static void shareRead(
      TestHelpers.OrgSession owner, DatabaseId dbId, MembershipId trusted) {
    var statement =
        new PolicyStatement(
            PolicyEffect.ALLOW,
            Set.of(trusted.value()),
            List.of(new PolicyAction(PolicyResourceType.DATABASE, PolicyVerb.READ)),
            List.of(new PolicyResourcePattern(PolicyResourceType.DATABASE, dbId.value())),
            null);
    var policyId = TestHelpers.createPolicyIn(owner, "share-read", List.of(statement));
    TestHelpers.setDatabasePoliciesAs(owner.token(), dbId, List.of(policyId)).statusCode(200);
  }

  private static PolicyId conditionalUpdatePolicy(
      TestHelpers.OrgSession org, String name, String condition) {
    var statement =
        new PolicyStatement(
            PolicyEffect.ALLOW,
            null,
            List.of(new PolicyAction(PolicyResourceType.DATABASE, PolicyVerb.UPDATE)),
            List.of(new PolicyResourcePattern(PolicyResourceType.DATABASE, null)),
            condition);
    return TestHelpers.createPolicyIn(org, name, List.of(statement));
  }

  private record CrossOrg(
      TestHelpers.OrgSession acme,
      DatabaseId acmeDb,
      TestHelpers.OrgSession beachape,
      MembershipId lloydMembership) {}
}
