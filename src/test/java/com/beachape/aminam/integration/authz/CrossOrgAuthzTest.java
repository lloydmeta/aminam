package com.beachape.aminam.integration.authz;

import static java.util.UUID.randomUUID;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.beachape.aminam.app.routes.v1.orgs.models.PolicyIdsRequest;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyAction;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyEffect;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyResourcePattern;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyResourceType;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyStatement;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyVerb;
import com.beachape.aminam.domain.authz.models.PolicyId;
import com.beachape.aminam.domain.authz.models.PolicyId.CustomPolicyId;
import com.beachape.aminam.domain.authz.services.SystemPolicies;
import com.beachape.aminam.domain.databases.models.DatabaseId;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import com.beachape.aminam.integration.utils.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

// The cross-org sharing decision at the HTTP boundary: orgA owns a database and shares read access
// with joe's membership in beachape. Both sides must permit, and the trust is membership-scoped.
@QuarkusTest
final class CrossOrgAuthzTest {

  @Test
  void crossOrgIsDeniedBeforeSharing() {
    var crossOrgFixture = setup();

    // joe holds database:read on database:* via beachape manager role (the trusted side), but
    // with no resource policy on orgA's db the cross-org AND enforcement denies -> 404.
    TestHelpers.readDatabaseAs(crossOrgFixture.joeOrgB().token(), crossOrgFixture.orgADbId())
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void bothSidesPermitGivesReadOnlyAccess() {
    var crossOrgFixture = setup();
    shareRead(crossOrgFixture.orgA(), crossOrgFixture.orgADbId(), crossOrgFixture.joeMembership());

    // Read is shared (resource policy) and granted by identity role: 200, but read-only since
    // only database:read was shared, so update stays cross-org-denied.
    TestHelpers.readDatabaseAs(crossOrgFixture.joeOrgB().token(), crossOrgFixture.orgADbId())
        .statusCode(200)
        .body("editable", equalTo(false));
    TestHelpers.editDatabaseAs(
            crossOrgFixture.joeOrgB().token(), crossOrgFixture.orgADbId(), "renamed")
        .statusCode(403)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void removingTheResourcePolicyRevokes() {
    var crossOrgFixture = setup();
    shareRead(crossOrgFixture.orgA(), crossOrgFixture.orgADbId(), crossOrgFixture.joeMembership());
    TestHelpers.readDatabaseAs(crossOrgFixture.joeOrgB().token(), crossOrgFixture.orgADbId())
        .statusCode(200);

    // Replace the share set with the empty set: the resource policy is detached.
    TestHelpers.setDatabasePoliciesAs(
            crossOrgFixture.orgA().token(), crossOrgFixture.orgADbId(), List.of())
        .statusCode(200);

    TestHelpers.readDatabaseAs(crossOrgFixture.joeOrgB().token(), crossOrgFixture.orgADbId())
        .statusCode(404);
  }

  @Test
  void theShareListIsReadableByTheOwningManagerAndReflectsTheShares() {
    var crossOrgFixture = setup();
    TestHelpers.databasePoliciesAs(crossOrgFixture.orgA().token(), crossOrgFixture.orgADbId())
        .statusCode(200)
        .body("values", equalTo(List.of()));

    var policyId =
        shareRead(
            crossOrgFixture.orgA(), crossOrgFixture.orgADbId(), crossOrgFixture.joeMembership());

    TestHelpers.databasePoliciesAs(crossOrgFixture.orgA().token(), crossOrgFixture.orgADbId())
        .statusCode(200)
        .body("values", equalTo(List.of(policyId.asText())));
  }

  @Test
  void removingTheTrustedIdentityRevokes() {
    var crossOrgFixture = setup();
    shareRead(crossOrgFixture.orgA(), crossOrgFixture.orgADbId(), crossOrgFixture.joeMembership());
    TestHelpers.readDatabaseAs(crossOrgFixture.joeOrgB().token(), crossOrgFixture.orgADbId())
        .statusCode(200);

    // joe strips own beachape roles, losing the identity-side database:read -> idPermit false.
    TestHelpers.setMemberPoliciesAs(
        crossOrgFixture.joeOrgB(), crossOrgFixture.joe().username(), List.of());

    TestHelpers.readDatabaseAs(crossOrgFixture.joeOrgB().token(), crossOrgFixture.orgADbId())
        .statusCode(404);
  }

  @Test
  void trustIsScopedToTheNamedMembership() {
    var crossOrgFixture = setup();
    shareRead(crossOrgFixture.orgA(), crossOrgFixture.orgADbId(), crossOrgFixture.joeMembership());

    // Active in beachape (the named membership) -> allowed.
    TestHelpers.readDatabaseAs(crossOrgFixture.joeOrgB().token(), crossOrgFixture.orgADbId())
        .statusCode(200);
    // The same person active in home org (a different membership) is not the named trustee -> 404.
    TestHelpers.readDatabaseAs(crossOrgFixture.joe().token(), crossOrgFixture.orgADbId())
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void outsiderCannotShareOrListShares404() {
    var acme = TestHelpers.managedOrg();
    var dbId = TestHelpers.createDatabaseIn(acme, "metrics");
    var outsider = TestHelpers.newAccount();

    TestHelpers.setDatabasePoliciesAs(
            outsider.token(), dbId, List.of(new CustomPolicyId(randomUUID())))
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
    TestHelpers.databasePoliciesAs(outsider.token(), dbId)
        .statusCode(404)
        .contentType(ContentType.JSON);
  }

  @Test
  void viewerCannotShare403ButCanListShares() {
    var acme = TestHelpers.managedOrg();
    var dbId = TestHelpers.createDatabaseIn(acme, "metrics");
    var viewer = TestHelpers.createNewUserAsMemberIn(acme, List.of(SystemPolicies.VIEWER));

    TestHelpers.setDatabasePoliciesAs(viewer, dbId, List.of(new CustomPolicyId(randomUUID())))
        .statusCode(403)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
    // A viewer can read the database, so it can read its share list (read-level visibility).
    TestHelpers.databasePoliciesAs(viewer, dbId).statusCode(200).body("values", equalTo(List.of()));
  }

  @Test
  void malformedPolicyIdIs400() {
    var acme = TestHelpers.managedOrg();
    var dbId = TestHelpers.createDatabaseIn(acme, "metrics");

    // A non-PolicyId string can't go through the typed helper, so build the request directly.
    TestHelpers.authed(acme.token())
        .contentType(ContentType.JSON)
        .body(new PolicyIdsRequest(List.of("not a policy")))
        .when()
        .put("/api/v1/databases/" + dbId.value() + "/policies")
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON);
  }

  @Test
  void cannotShareAPolicyOwnedByAnotherOrg400() {
    var acme = TestHelpers.managedOrg();
    var dbId = TestHelpers.createDatabaseIn(acme, "metrics");
    // A custom policy authored in (and owned by) a different org. orgA holds database:attach on its
    // own db, but policy:read on a foreign policy is cross-org-denied, so it is not assignable ->
    // the replace fails atomically (400 invalid policies).
    var beachape = TestHelpers.managedOrg();
    var foreign = authorReadPolicy(beachape, dbId, new MembershipId(randomUUID()));

    TestHelpers.setDatabasePoliciesAs(acme.token(), dbId, List.of(foreign))
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("message", equalTo("invalid policies"));
  }

  private static CrossOrg setup() {
    var acme = TestHelpers.managedOrg();
    var dbId = TestHelpers.createDatabaseIn(acme, "metrics");
    var joe = TestHelpers.newAccount();
    var beachapeId = TestHelpers.createOrgAs(joe.token(), "beachape");
    var joeBeachape = TestHelpers.switchOrgAs(joe.token(), beachapeId);
    var joeMembership = TestHelpers.getMembershipIdByUsernameAs(joeBeachape, joe.username());
    return new CrossOrg(acme, dbId, joe, joeBeachape, joeMembership);
  }

  private static PolicyId shareRead(
      TestHelpers.OrgSession acme, DatabaseId dbId, MembershipId trusted) {
    var policyId = authorReadPolicy(acme, dbId, trusted);
    TestHelpers.setDatabasePoliciesAs(acme.token(), dbId, List.of(policyId)).statusCode(200);
    return policyId;
  }

  private static PolicyId authorReadPolicy(
      TestHelpers.OrgSession org, DatabaseId dbId, MembershipId trusted) {
    var statement =
        new PolicyStatement(
            PolicyEffect.ALLOW,
            Set.of(trusted.value()),
            List.of(new PolicyAction(PolicyResourceType.DATABASE, PolicyVerb.READ)),
            List.of(new PolicyResourcePattern(PolicyResourceType.DATABASE, dbId.value())),
            null);
    return TestHelpers.createPolicyIn(org, "share-read", List.of(statement));
  }

  private record CrossOrg(
      TestHelpers.OrgSession orgA,
      DatabaseId orgADbId,
      TestHelpers.Account joe,
      TestHelpers.OrgSession joeOrgB,
      MembershipId joeMembership) {}
}
