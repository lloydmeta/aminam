package com.beachape.aminam.integration.authz;

import com.beachape.aminam.app.routes.v1.policies.models.PolicyAction;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyEffect;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyResourcePattern;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyResourceType;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyStatement;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyVerb;
import com.beachape.aminam.domain.authc.models.AccessToken;
import com.beachape.aminam.domain.authz.models.PolicyId;
import com.beachape.aminam.domain.authz.services.SystemPolicies;
import com.beachape.aminam.integration.utils.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
final class CelConditionAuthzTest {

  @Test
  void aNamePrefixConditionGatesTheGrant() {
    var org = TestHelpers.managedOrg();
    var policyId = updatePolicy(org, "report-editors", "resource.name.startsWith('report-')");
    var reportDb = TestHelpers.createDatabaseIn(org, "report-sales");
    var otherDb = TestHelpers.createDatabaseIn(org, "ledger");
    var member = memberWith(org, policyId);

    TestHelpers.editDatabaseAs(member, reportDb, "edited-" + reportDb.value()).statusCode(200);
    // Readable via system:viewer but the condition is false for this name, so update is 403.
    TestHelpers.editDatabaseAs(member, otherDb, "edited-" + otherDb.value()).statusCode(403);
  }

  @Test
  void aRegexMatchesConditionGatesTheGrant() {
    var org = TestHelpers.managedOrg();
    var policyId = updatePolicy(org, "slug-editors", "resource.name.matches('^[a-z0-9-]+$')");
    var slugDb = TestHelpers.createDatabaseIn(org, "valid-slug-1");
    var spacedDb = TestHelpers.createDatabaseIn(org, "Has Spaces");
    var member = memberWith(org, policyId);

    TestHelpers.editDatabaseAs(member, slugDb, "edited-" + slugDb.value()).statusCode(200);
    TestHelpers.editDatabaseAs(member, spacedDb, "edited-" + spacedDb.value()).statusCode(403);
  }

  @Test
  void aServerResolvedOrgConditionGatesTheGrant() {
    // resource.org_id == principal.active_org is true for an in-org database, so update is allowed.
    var org = TestHelpers.managedOrg();
    var policyId = updatePolicy(org, "own-org-editors", "resource.org_id == principal.active_org");
    var db = TestHelpers.createDatabaseIn(org, "anything");
    var member = memberWith(org, policyId);

    TestHelpers.editDatabaseAs(member, db, "edited-" + db.value()).statusCode(200);
  }

  @Test
  void anAlwaysFalseConditionNeverGrants() {
    var org = TestHelpers.managedOrg();
    var policyId =
        updatePolicy(org, "never-editors", "resource.name == 'this' && resource.name == 'that'");
    var db = TestHelpers.createDatabaseIn(org, "report-x");
    var member = memberWith(org, policyId);

    TestHelpers.editDatabaseAs(member, db, "edited-" + db.value()).statusCode(403);
  }

  private static AccessToken memberWith(TestHelpers.OrgSession org, PolicyId policyId) {
    var member = TestHelpers.newAccount();
    TestHelpers.addMemberToOrg(org, member.username(), List.of(SystemPolicies.VIEWER, policyId));
    return TestHelpers.switchOrgAs(member.token(), org.id()).token();
  }

  private static PolicyId updatePolicy(TestHelpers.OrgSession org, String name, String condition) {
    var statement =
        new PolicyStatement(
            PolicyEffect.ALLOW,
            null,
            List.of(new PolicyAction(PolicyResourceType.DATABASE, PolicyVerb.UPDATE)),
            List.of(new PolicyResourcePattern(PolicyResourceType.DATABASE, null)),
            condition);
    return TestHelpers.createPolicyIn(org, name, List.of(statement));
  }
}
