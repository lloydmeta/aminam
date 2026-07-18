package com.beachape.aminam.integration.authz;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;

import com.beachape.aminam.app.routes.v1.policies.models.PolicyAction;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyEffect;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyResourcePattern;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyResourceType;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyStatement;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyVerb;
import com.beachape.aminam.domain.authz.models.PolicyId;
import com.beachape.aminam.domain.databases.models.DatabaseId;
import com.beachape.aminam.integration.utils.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

@QuarkusTest
final class CreatorPolicyAuthzTest {

  private static final String OWNS_IT = "resource.created_by == principal.id";

  @Test
  void aCreatorReadsUpdatesAndDeletesTheirOwnDatabase() {
    var f = creators();

    TestHelpers.readDatabaseAs(f.alice().token(), f.aliceDb())
        .statusCode(200)
        .body("editable", equalTo(true));
    TestHelpers.editDatabaseAs(f.alice().token(), f.aliceDb(), "renamed").statusCode(200);
    TestHelpers.deleteDatabaseAs(f.alice().token(), f.aliceDb()).statusCode(200);
  }

  @Test
  void aCreatorGets404OnAnotherMembersDatabase() {
    var f = creators();

    // 404 rather than 403: the ownership condition denies read, so the visibility gate fails before
    // any permit is considered and the database is invisible rather than forbidden.
    TestHelpers.readDatabaseAs(f.alice().token(), f.bobDb()).statusCode(404);
    TestHelpers.editDatabaseAs(f.alice().token(), f.bobDb(), "hijacked").statusCode(404);
    TestHelpers.deleteDatabaseAs(f.alice().token(), f.bobDb()).statusCode(404);
    TestHelpers.readDatabaseAs(f.bob().token(), f.aliceDb()).statusCode(404);
  }

  @Test
  void aCreatorsListShowsOnlyTheirOwnDatabases() {
    var f = creators();

    TestHelpers.listDatabasesAs(f.alice().token(), f.org().id())
        .statusCode(200)
        .body("values.name", contains("alice-db"));
    TestHelpers.listDatabasesAs(f.bob().token(), f.org().id())
        .statusCode(200)
        .body("values.name", contains("bob-db"));
  }

  @Test
  void aCreatorCreatesDatabasesFreely() {
    var f = creators();

    // Create is unconditioned, so it stays open however many the member already owns.
    TestHelpers.createDatabaseAs(f.alice().token(), f.org().id(), "alice-second").statusCode(201);
  }

  @Test
  void aConditionOnTheCreateStatementDeniesEveryCreate() {
    var org = TestHelpers.managedOrg();
    var policyId =
        TestHelpers.createPolicyIn(
            org, "conditioned-create", List.of(orgRead(), databaseCreate(OWNS_IT)));
    var member = memberWith(org, policyId);

    // A ToCreate carries no facts, so an ownership condition on create can never hold. This is why
    // the working policy keeps create unconditioned and gates only read, update and delete.
    TestHelpers.createDatabaseAs(member.token(), org.id(), "never").statusCode(403);
  }

  @Test
  void theOrgManagerStillSeesEveryCreatorsDatabase() {
    var f = creators();

    TestHelpers.listDatabasesAs(f.org().token(), f.org().id())
        .statusCode(200)
        .body("values.name", contains("alice-db", "bob-db"));
  }

  private record Creators(
      TestHelpers.OrgSession org,
      TestHelpers.OrgSession alice,
      DatabaseId aliceDb,
      TestHelpers.OrgSession bob,
      DatabaseId bobDb) {}

  private static Creators creators() {
    var org = TestHelpers.managedOrg();
    var policyId =
        TestHelpers.createPolicyIn(
            org, "own-databases", List.of(orgRead(), databaseCreate(null), databaseRud(OWNS_IT)));
    var alice = memberWith(org, policyId);
    var bob = memberWith(org, policyId);
    return new Creators(
        org,
        alice,
        TestHelpers.createDatabaseIn(alice, "alice-db"),
        bob,
        TestHelpers.createDatabaseIn(bob, "bob-db"));
  }

  /// Holds the creator policy alone: no system:viewer, so read is governed only by the condition.
  private static TestHelpers.OrgSession memberWith(TestHelpers.OrgSession org, PolicyId policyId) {
    var member = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(org, member.username(), List.of(policyId));
    return TestHelpers.switchOrgAs(member.token(), org.id());
  }

  /// Needed because create and list both gate on the org being visible first.
  private static PolicyStatement orgRead() {
    return new PolicyStatement(
        PolicyEffect.ALLOW,
        null,
        List.of(new PolicyAction(PolicyResourceType.ORG, PolicyVerb.READ)),
        List.of(new PolicyResourcePattern(PolicyResourceType.ORG, null)),
        null);
  }

  private static PolicyStatement databaseCreate(@Nullable String condition) {
    return new PolicyStatement(
        PolicyEffect.ALLOW,
        null,
        List.of(new PolicyAction(PolicyResourceType.DATABASE, PolicyVerb.CREATE)),
        List.of(new PolicyResourcePattern(PolicyResourceType.DATABASE, null)),
        condition);
  }

  private static PolicyStatement databaseRud(String condition) {
    return new PolicyStatement(
        PolicyEffect.ALLOW,
        null,
        List.of(
            new PolicyAction(PolicyResourceType.DATABASE, PolicyVerb.READ),
            new PolicyAction(PolicyResourceType.DATABASE, PolicyVerb.UPDATE),
            new PolicyAction(PolicyResourceType.DATABASE, PolicyVerb.DELETE)),
        List.of(new PolicyResourcePattern(PolicyResourceType.DATABASE, null)),
        condition);
  }
}
