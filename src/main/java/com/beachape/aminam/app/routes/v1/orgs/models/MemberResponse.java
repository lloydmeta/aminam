package com.beachape.aminam.app.routes.v1.orgs.models;

import com.beachape.aminam.domain.authz.models.PolicyId;
import com.beachape.aminam.domain.orgs.services.OrganisationService.MembershipDetails;
import java.util.List;

public record MemberResponse(
    String membershipId, String userId, String username, List<String> policyIds) {

  public static MemberResponse from(MembershipDetails membershipDetails) {
    return new MemberResponse(
        membershipDetails.membershipId().value().toString(),
        membershipDetails.userId().value().toString(),
        membershipDetails.username(),
        membershipDetails.policyIds().stream().map(PolicyId::asText).toList());
  }
}
