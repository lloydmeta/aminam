package com.beachape.aminam.app.routes.v1.databases;

import com.beachape.aminam.app.authc.Principals;
import com.beachape.aminam.app.models.EmptyResponse;
import com.beachape.aminam.app.routes.v1.databases.models.DatabasePoliciesResponse;
import com.beachape.aminam.app.routes.v1.databases.models.DatabaseResponse;
import com.beachape.aminam.app.routes.v1.databases.models.UpdateDatabaseRequest;
import com.beachape.aminam.app.routes.v1.orgs.models.PolicyIdsRequest;
import com.beachape.aminam.domain.authz.models.PolicyId;
import com.beachape.aminam.domain.authz.models.PolicyId.MalformedPolicyIdException;
import com.beachape.aminam.domain.authz.services.PolicyAuthzService.InvalidPoliciesException;
import com.beachape.aminam.domain.databases.models.DatabaseId;
import com.beachape.aminam.domain.databases.services.DatabaseService;
import com.beachape.aminam.domain.errors.NotAuthorisedException;
import com.beachape.aminam.domain.errors.NotFoundException;
import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import java.util.UUID;

@ApplicationScoped
@RunOnVirtualThread
@Authenticated
public class DatabaseResource {

  @Inject DatabaseService databases;

  @GET
  @Path("{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public DatabaseResponse get(@PathParam("id") UUID id, @Context SecurityContext security)
      throws NotFoundException {
    var user = Principals.requireUser(security);
    return DatabaseResponse.from(databases.get(user, new DatabaseId(id)));
  }

  @PUT
  @Path("{id}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public DatabaseResponse update(
      @PathParam("id") UUID id,
      @Valid UpdateDatabaseRequest request,
      @Context SecurityContext security)
      throws NotFoundException, NotAuthorisedException {
    var user = Principals.requireUser(security);
    var updated = databases.updateName(user, new DatabaseId(id), request.name());
    return DatabaseResponse.of(updated, /* editable= */ true);
  }

  @DELETE
  @Path("{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public EmptyResponse delete(@PathParam("id") UUID id, @Context SecurityContext security)
      throws NotFoundException, NotAuthorisedException {
    var user = Principals.requireUser(security);
    databases.delete(user, new DatabaseId(id));
    return new EmptyResponse();
  }

  @GET
  @Path("{id}/policies")
  @Produces(MediaType.APPLICATION_JSON)
  public DatabasePoliciesResponse policies(
      @PathParam("id") UUID id, @Context SecurityContext security) throws NotFoundException {
    var user = Principals.requireUser(security);
    return DatabasePoliciesResponse.from(databases.listPolicies(user, new DatabaseId(id)));
  }

  @PUT
  @Path("{id}/policies")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public DatabasePoliciesResponse setPolicies(
      @PathParam("id") UUID id, @Valid PolicyIdsRequest request, @Context SecurityContext security)
      throws NotFoundException,
          NotAuthorisedException,
          InvalidPoliciesException,
          MalformedPolicyIdException {
    var user = Principals.requireUser(security);
    var updated =
        databases.setPolicies(user, new DatabaseId(id), PolicyId.fromRaw(request.policyIds()));
    return DatabasePoliciesResponse.from(updated);
  }
}
