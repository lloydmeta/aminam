package com.beachape.aminam.app.routes.v1.orgs.models;

import com.beachape.aminam.domain.orgs.models.Organisation;

public record OrgResponse(String id, String name, String createdBy, String createdAt) {

  public static OrgResponse from(Organisation org) {
    return new OrgResponse(
        org.id().value().toString(),
        org.name(),
        org.createdBy().value().toString(),
        org.createdAt().toString());
  }
}
