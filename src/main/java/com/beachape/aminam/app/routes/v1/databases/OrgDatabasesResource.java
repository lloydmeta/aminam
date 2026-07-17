package com.beachape.aminam.app.routes.v1.databases;

import com.beachape.aminam.app.authc.Principals;
import com.beachape.aminam.app.routes.v1.databases.models.CreateDatabaseRequest;
import com.beachape.aminam.app.routes.v1.databases.models.DatabaseResponse;
import com.beachape.aminam.app.routes.v1.databases.models.DatabasesResponse;
import com.beachape.aminam.domain.databases.services.DatabaseService;
import com.beachape.aminam.domain.errors.NotAuthorisedException;
import com.beachape.aminam.domain.errors.NotFoundException;
import com.beachape.aminam.domain.orgs.models.OrgId;
import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.resteasy.reactive.ResponseStatus;

/// Org-scoped database endpoints, mounted by OrgResource at `/orgs/{id}/databases`.
@ApplicationScoped
@RunOnVirtualThread
@Authenticated
public class OrgDatabasesResource {

  @Inject DatabaseService databases;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ResponseStatus(201)
  @APIResponse(responseCode = "201")
  public DatabaseResponse create(
      @PathParam("id") UUID orgId,
      @Valid CreateDatabaseRequest request,
      @Context SecurityContext security)
      throws NotFoundException, NotAuthorisedException {
    var user = Principals.requireUser(security);
    var created = databases.create(user, new OrgId(orgId), request.name());
    return DatabaseResponse.of(created, /* editable= */ true);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public DatabasesResponse list(@PathParam("id") UUID orgId, @Context SecurityContext security)
      throws NotFoundException {
    var user = Principals.requireUser(security);
    return new DatabasesResponse(
        databases.list(user, new OrgId(orgId)).stream().map(DatabaseResponse::from).toList());
  }
}
