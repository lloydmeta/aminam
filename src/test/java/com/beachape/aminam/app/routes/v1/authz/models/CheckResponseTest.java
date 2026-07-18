package com.beachape.aminam.app.routes.v1.authz.models;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.domain.authz.models.Action;
import com.beachape.aminam.domain.authz.models.AuthzDecision;
import com.beachape.aminam.domain.authz.models.ResourceRef;
import com.beachape.aminam.domain.authz.models.ResourceType;
import com.beachape.aminam.domain.authz.models.Verb;
import com.beachape.aminam.domain.orgs.models.OrgId;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CheckResponseTest {

  @Test
  void allOkWhenEveryDecisionIsAllowed() {
    var response =
        CheckResponse.from(List.of(new AuthzDecision.Allow("a"), new AuthzDecision.Allow("b")));

    assertThat(response.allOk()).isTrue();
    assertThat(response.results())
        .containsExactly(
            new CheckResult(/* ok= */ true, "a"), new CheckResult(/* ok= */ true, "b"));
  }

  @Test
  void notAllOkWhenAnyDecisionIsDenied() {
    var response =
        CheckResponse.from(List.of(new AuthzDecision.Allow("a"), new AuthzDecision.Deny("b")));

    assertThat(response.allOk()).isFalse();
  }

  @Test
  void resultsMirrorInputOrderAndCarryTheReasonFromBothVariants() {
    var response =
        CheckResponse.from(
            List.of(
                new AuthzDecision.Deny("resource not found"),
                new AuthzDecision.Allow("system:viewer")));

    assertThat(response.results())
        .containsExactly(
            new CheckResult(/* ok= */ false, "resource not found"),
            new CheckResult(/* ok= */ true, "system:viewer"));
  }

  @Test
  void existingCheckMapsToAnExistingRefSharingItsType() {
    var id = randomUUID();

    var check = new CheckItem.Existing(AuthzResourceType.DATABASE, AuthzVerb.UPDATE, id).toDomain();

    assertThat(check.action()).isEqualTo(new Action(ResourceType.DATABASE, Verb.UPDATE));
    assertThat(check.resource()).isEqualTo(new ResourceRef.Existing(ResourceType.DATABASE, id));
  }

  @Test
  void toCreateCheckMapsToAToCreateRefScopedByOwningOrg() {
    var owningOrgId = randomUUID();

    var check =
        new CheckItem.ToCreate(AuthzResourceType.DATABASE, AuthzVerb.CREATE, owningOrgId)
            .toDomain();

    assertThat(check.action()).isEqualTo(new Action(ResourceType.DATABASE, Verb.CREATE));
    assertThat(check.resource())
        .isEqualTo(new ResourceRef.ToCreate(ResourceType.DATABASE, new OrgId(owningOrgId)));
  }
}
