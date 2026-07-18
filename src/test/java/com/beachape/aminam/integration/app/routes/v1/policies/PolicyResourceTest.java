package com.beachape.aminam.integration.app.routes.v1.policies;

import static io.restassured.RestAssured.given;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toSet;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

import com.beachape.aminam.app.routes.v1.orgs.models.PolicyIdsRequest;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyAction;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyEffect;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyRequest;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyResourcePattern;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyResourceType;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyStatement;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyVerb;
import com.beachape.aminam.domain.authz.models.PolicyId;
import com.beachape.aminam.domain.authz.services.SystemPolicies;
import com.beachape.aminam.integration.utils.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

@QuarkusTest
final class PolicyResourceTest {

  @Test
  void managerAuthorsListsGetsUpdatesAndDeletesAPolicy() {
    var org = TestHelpers.managedOrg();

    var id =
        TestHelpers.authed(org.token())
            .contentType(ContentType.JSON)
            .body(policy("reports", "resource.name.startsWith('report-')"))
            .when()
            .post("/api/v1/orgs/" + org.id() + "/policies")
            .then()
            .statusCode(201)
            .body("name", equalTo("reports"))
            .body("id", notNullValue())
            .body("statements[0].effect", equalTo("ALLOW"))
            .body("statements[0].actions[0].type", equalTo("DATABASE"))
            .body("statements[0].actions[0].verb", equalTo("UPDATE"))
            .body("statements[0].resources[0].type", equalTo("DATABASE"))
            .body("statements[0].resources[0].id", equalTo(null))
            .body("statements[0].condition", equalTo("resource.name.startsWith('report-')"))
            .extract()
            .path("id");

    TestHelpers.authed(org.token())
        .when()
        .get("/api/v1/orgs/" + org.id() + "/policies")
        .then()
        .statusCode(200)
        .body("values.id", contains(id));

    TestHelpers.authed(org.token())
        .when()
        .get("/api/v1/orgs/" + org.id() + "/policies/" + id)
        .then()
        .statusCode(200)
        .body("name", equalTo("reports"));

    TestHelpers.authed(org.token())
        .contentType(ContentType.JSON)
        .body(policy("renamed", null))
        .when()
        .put("/api/v1/orgs/" + org.id() + "/policies/" + id)
        .then()
        .statusCode(200)
        .body("name", equalTo("renamed"))
        .body("statements[0].condition", equalTo(null));

    TestHelpers.authed(org.token())
        .when()
        .delete("/api/v1/orgs/" + org.id() + "/policies/" + id)
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON);

    TestHelpers.authed(org.token())
        .when()
        .get("/api/v1/orgs/" + org.id() + "/policies/" + id)
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON);
  }

  @Test
  void listReturnsPoliciesInCreationOrder() {
    var org = TestHelpers.managedOrg();
    createPolicy(org, "first-policy", null);
    createPolicy(org, "second-policy", null);

    TestHelpers.authed(org.token())
        .when()
        .get("/api/v1/orgs/" + org.id() + "/policies")
        .then()
        .statusCode(200)
        .body("values.name", contains("first-policy", "second-policy"));
  }

  @Test
  void aViewerCannotAuthorAndGets403() {
    var org = TestHelpers.managedOrg();
    var viewer = TestHelpers.createNewUserAsMemberIn(org, List.of(SystemPolicies.VIEWER));

    TestHelpers.authed(viewer)
        .contentType(ContentType.JSON)
        .body(policy("p", null))
        .when()
        .post("/api/v1/orgs/" + org.id() + "/policies")
        .then()
        .statusCode(403)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void anOutsiderCannotSeeTheOrgAndGets404() {
    var org = TestHelpers.managedOrg();
    var outsider = TestHelpers.newAccount();

    TestHelpers.authed(outsider.token())
        .contentType(ContentType.JSON)
        .body(policy("p", null))
        .when()
        .post("/api/v1/orgs/" + org.id() + "/policies")
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void aManagerOfAnotherOrgCannotAuthorAcrossOrgs() {
    var acme = TestHelpers.managedOrg();
    var beachape = TestHelpers.managedOrg();

    TestHelpers.authed(beachape.token())
        .contentType(ContentType.JSON)
        .body(policy("p", null))
        .when()
        .post("/api/v1/orgs/" + acme.id() + "/policies")
        .then()
        .statusCode(404)
        .contentType(ContentType.JSON);
  }

  @Test
  void aMalformedConditionIsRejectedWith400() {
    var org = TestHelpers.managedOrg();

    TestHelpers.authed(org.token())
        .contentType(ContentType.JSON)
        .body(policy("p", "resource.name =="))
        .when()
        .post("/api/v1/orgs/" + org.id() + "/policies")
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("message", equalTo("invalid policy"))
        .body("errors.size()", greaterThan(0))
        .body("errors[0].location", notNullValue());
  }

  @Test
  void anUnknownActionVerbIsRejectedWith400() {
    var org = TestHelpers.managedOrg();
    // Raw map to avoid validation of the verb enum in the request body.
    var statement =
        Map.of(
            "effect", "ALLOW",
            "actions", List.of(Map.of("type", "DATABASE", "verb", "FLY")),
            "resources", List.of(Map.of("type", "DATABASE")));

    TestHelpers.authed(org.token())
        .contentType(ContentType.JSON)
        .body(Map.of("name", "p", "statements", List.of(statement)))
        .when()
        .post("/api/v1/orgs/" + org.id() + "/policies")
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON);
  }

  @Test
  void aNonBooleanConditionIsRejectedWith400() {
    var org = TestHelpers.managedOrg();

    TestHelpers.authed(org.token())
        .contentType(ContentType.JSON)
        .body(policy("p", "resource.name"))
        .when()
        .post("/api/v1/orgs/" + org.id() + "/policies")
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("message", equalTo("invalid policy"));
  }

  @Test
  void namingMembershipsAuthorsAResourcePolicy() {
    var org = TestHelpers.managedOrg();
    var statement =
        new PolicyStatement(
            PolicyEffect.ALLOW,
            Set.of(randomUUID()),
            List.of(new PolicyAction(PolicyResourceType.DATABASE, PolicyVerb.UPDATE)),
            List.of(new PolicyResourcePattern(PolicyResourceType.DATABASE, null)),
            null);

    TestHelpers.authed(org.token())
        .contentType(ContentType.JSON)
        .body(new PolicyRequest("p", List.of(statement)))
        .when()
        .post("/api/v1/orgs/" + org.id() + "/policies")
        .then()
        .statusCode(201);
  }

  @Test
  void tooManyMembershipsIsRejectedWith400() {
    // 129 memberships breaches the statement's @Size(max = 128)
    var org = TestHelpers.managedOrg();
    var memberships = Stream.generate(() -> randomUUID()).limit(129).collect(toSet());
    var statement =
        new PolicyStatement(
            PolicyEffect.ALLOW,
            memberships,
            List.of(new PolicyAction(PolicyResourceType.DATABASE, PolicyVerb.UPDATE)),
            List.of(new PolicyResourcePattern(PolicyResourceType.DATABASE, null)),
            null);

    TestHelpers.authed(org.token())
        .contentType(ContentType.JSON)
        .body(new PolicyRequest("p", List.of(statement)))
        .when()
        .post("/api/v1/orgs/" + org.id() + "/policies")
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON);
  }

  @Test
  void authoringWithoutAuthReturns401Json() {
    given()
        .contentType(ContentType.JSON)
        .body(policy("p", null))
        .when()
        .post("/api/v1/orgs/" + randomUUID() + "/policies")
        .then()
        .statusCode(401)
        .contentType(ContentType.JSON)
        .body("message", notNullValue());
  }

  @Test
  void aSameOrgCustomPolicyCanBeAssignedToAMember() {
    var org = TestHelpers.managedOrg();
    var id = createPolicy(org, "reports", "resource.name.startsWith('report-')");
    var member = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(org, member.username(), SystemPolicies.VIEWER);

    TestHelpers.authed(org.token())
        .contentType(ContentType.JSON)
        .body(new PolicyIdsRequest(List.of("system:viewer", id.asText())))
        .when()
        .put("/api/v1/orgs/" + org.id() + "/members/" + member.username() + "/policies")
        .then()
        .statusCode(200)
        .body("policyIds", containsInAnyOrder("system:viewer", id.asText()));
  }

  @Test
  void aForeignCustomPolicyCannotBeAssigned() {
    var acme = TestHelpers.managedOrg();
    var foriengPolicyId = createPolicy(acme, "secret", null);
    var beachape = TestHelpers.managedOrg();
    var member = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(beachape, member.username(), SystemPolicies.VIEWER);

    TestHelpers.authed(beachape.token())
        .contentType(ContentType.JSON)
        .body(new PolicyIdsRequest(List.of("system:viewer", foriengPolicyId.asText())))
        .when()
        .put("/api/v1/orgs/" + beachape.id() + "/members/" + member.username() + "/policies")
        .then()
        .statusCode(400)
        .contentType(ContentType.JSON)
        .body("errors.policyId", hasItem(foriengPolicyId.asText()));
  }

  private static PolicyId createPolicy(
      TestHelpers.OrgSession org, String name, @Nullable String cond) {
    return TestHelpers.createPolicyIn(org, name, List.of(statement(cond)));
  }

  private static PolicyRequest policy(String name, @Nullable String condition) {
    return new PolicyRequest(name, List.of(statement(condition)));
  }

  private static PolicyStatement statement(@Nullable String condition) {
    return new PolicyStatement(
        PolicyEffect.ALLOW,
        null,
        List.of(new PolicyAction(PolicyResourceType.DATABASE, PolicyVerb.UPDATE)),
        List.of(new PolicyResourcePattern(PolicyResourceType.DATABASE, null)), // id null = wildcard
        condition);
  }
}
