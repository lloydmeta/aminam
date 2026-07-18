package com.beachape.aminam.integration.utils;

import static io.restassured.RestAssured.given;
import static java.util.UUID.randomUUID;

import com.beachape.aminam.app.routes.v1.authc.models.LoginRequest;
import com.beachape.aminam.app.routes.v1.authc.models.SignupRequest;
import com.beachape.aminam.app.routes.v1.databases.models.CreateDatabaseRequest;
import com.beachape.aminam.app.routes.v1.databases.models.UpdateDatabaseRequest;
import com.beachape.aminam.app.routes.v1.orgs.models.AddMemberRequest;
import com.beachape.aminam.app.routes.v1.orgs.models.CreateOrgRequest;
import com.beachape.aminam.app.routes.v1.orgs.models.PolicyIdsRequest;
import com.beachape.aminam.app.routes.v1.orgs.models.SwitchOrgRequest;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyRequest;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyStatement;
import com.beachape.aminam.domain.authc.models.AccessToken;
import com.beachape.aminam.domain.authz.models.PolicyId;
import com.beachape.aminam.domain.authz.services.SystemPolicies;
import com.beachape.aminam.domain.databases.models.DatabaseId;
import com.beachape.aminam.domain.orgs.models.MembershipId;
import com.beachape.aminam.domain.orgs.models.OrgId;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import java.util.List;
import java.util.UUID;

/// Shared REST-Assured helpers for the signup/login/org/database lifecycle.
public final class TestHelpers {

  private static final String PASSWORD = "passw0rd";

  public static RequestSpecification authed(AccessToken token) {
    return given().header("Authorization", "Bearer " + token.value());
  }

  public static String signUp() {
    var username = "user" + randomUUID().toString().replace("-", "");
    given()
        .contentType(ContentType.JSON)
        .body(new SignupRequest(username, PASSWORD))
        .when()
        .post("/api/v1/signup")
        .then()
        .statusCode(201);
    return username;
  }

  public static AccessToken logIn(String username, String password) {
    return new AccessToken(
        given()
            .contentType(ContentType.JSON)
            .body(new LoginRequest(username, password))
            .when()
            .post("/api/v1/login")
            .then()
            .statusCode(200)
            .extract()
            .path("token"));
  }

  public static Account newAccount() {
    var username = signUp();
    return new Account(username, logIn(username, PASSWORD));
  }

  public static OrgId createOrgAs(AccessToken token, String name) {
    String orgIdRaw =
        authed(token)
            .contentType(ContentType.JSON)
            .body(new CreateOrgRequest(name))
            .when()
            .post("/api/v1/orgs")
            .then()
            .statusCode(201)
            .extract()
            .path("id");
    return new OrgId(UUID.fromString(orgIdRaw));
  }

  public static OrgSession switchOrgAs(AccessToken token, OrgId orgId) {
    var switched =
        new AccessToken(
            authed(token)
                .contentType(ContentType.JSON)
                .body(new SwitchOrgRequest(orgId.value().toString()))
                .when()
                .post("/api/v1/sessions/switch-org")
                .then()
                .statusCode(200)
                .extract()
                .path("token"));
    return new OrgSession(switched, orgId);
  }

  public static OrgSession managedOrg() {
    return managedOrg("beachape");
  }

  /// A fresh org of the given name whose creator (a manager) is active in it.
  public static OrgSession managedOrg(String orgName) {
    var owner = newAccount();
    var orgId = createOrgAs(owner.token(), orgName);
    return switchOrgAs(owner.token(), orgId);
  }

  public static void addMemberToOrg(OrgSession session, String username, PolicyId policyId) {
    addMemberToOrg(session, username, List.of(policyId));
  }

  public static void addMemberToOrg(OrgSession session, String username, List<PolicyId> policyIds) {
    if (policyIds.isEmpty()) {
      // The add-member endpoint requires at least one policy, so seat a role-less member by adding
      // with a viewer role then stripping it.
      addMemberToOrg(session, username, List.of(SystemPolicies.VIEWER));
      setMemberPoliciesAs(session, username, List.of());
      return;
    }
    authed(session.token())
        .contentType(ContentType.JSON)
        .body(new AddMemberRequest(username, policyIds.stream().map(PolicyId::asText).toList()))
        .when()
        .post("/api/v1/orgs/" + session.id().value() + "/members")
        .then()
        .statusCode(201);
  }

  public static void setMemberPoliciesAs(
      OrgSession session, String username, List<PolicyId> policyIds) {
    authed(session.token())
        .contentType(ContentType.JSON)
        .body(new PolicyIdsRequest(policyIds.stream().map(PolicyId::asText).toList()))
        .when()
        .put("/api/v1/orgs/" + session.id().value() + "/members/" + username + "/policies")
        .then()
        .statusCode(200);
  }

  public static AccessToken createNewUserAsMemberIn(OrgSession org, List<PolicyId> roles) {
    var member = newAccount();
    addMemberToOrg(org, member.username(), roles);
    return switchOrgAs(member.token(), org.id()).token();
  }

  public static ValidatableResponse createDatabaseAs(AccessToken token, OrgId orgId, String name) {
    return authed(token)
        .contentType(ContentType.JSON)
        .body(new CreateDatabaseRequest(name))
        .when()
        .post("/api/v1/orgs/" + orgId.value() + "/databases")
        .then();
  }

  public static DatabaseId createDatabaseIn(OrgSession org, String name) {
    String id = createDatabaseAs(org.token(), org.id(), name).statusCode(201).extract().path("id");
    return new DatabaseId(UUID.fromString(id));
  }

  public static MembershipId getMembershipIdByUsernameAs(OrgSession session, String username) {
    String raw =
        authed(session.token())
            .when()
            .get("/api/v1/orgs/" + session.id().value() + "/members")
            .then()
            .statusCode(200)
            .extract()
            .path("values.find { it.username == '" + username + "' }.membershipId");
    return new MembershipId(UUID.fromString(raw));
  }

  public static ValidatableResponse listDatabasesAs(AccessToken token, OrgId orgId) {
    return authed(token).when().get("/api/v1/orgs/" + orgId.value() + "/databases").then();
  }

  public static ValidatableResponse readDatabaseAs(AccessToken token, DatabaseId dbId) {
    return authed(token).when().get("/api/v1/databases/" + dbId.value()).then();
  }

  public static ValidatableResponse editDatabaseAs(
      AccessToken token, DatabaseId dbId, String name) {
    return authed(token)
        .contentType(ContentType.JSON)
        .body(new UpdateDatabaseRequest(name))
        .when()
        .put("/api/v1/databases/" + dbId.value())
        .then();
  }

  public static ValidatableResponse deleteDatabaseAs(AccessToken token, DatabaseId dbId) {
    return authed(token).when().delete("/api/v1/databases/" + dbId.value()).then();
  }

  public static ValidatableResponse databasePoliciesAs(AccessToken token, DatabaseId dbId) {
    return authed(token).when().get("/api/v1/databases/" + dbId.value() + "/policies").then();
  }

  public static ValidatableResponse setDatabasePoliciesAs(
      AccessToken token, DatabaseId dbId, List<PolicyId> policyIds) {
    return authed(token)
        .contentType(ContentType.JSON)
        .body(new PolicyIdsRequest(policyIds.stream().map(PolicyId::asText).toList()))
        .when()
        .put("/api/v1/databases/" + dbId.value() + "/policies")
        .then();
  }

  public static PolicyId createPolicyIn(
      OrgSession org, String name, List<PolicyStatement> statements) {
    return PolicyId.unsafeFromStoredText(
        authed(org.token)
            .contentType(ContentType.JSON)
            .body(new PolicyRequest(name, statements))
            .when()
            .post("/api/v1/orgs/" + org.id.value() + "/policies")
            .then()
            .statusCode(201)
            .extract()
            .path("id"));
  }

  public record Account(String username, AccessToken token) {}

  public record OrgSession(AccessToken token, OrgId id) {}

  private TestHelpers() {}
}
