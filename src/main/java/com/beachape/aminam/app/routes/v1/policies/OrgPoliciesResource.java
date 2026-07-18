package com.beachape.aminam.app.routes.v1.policies;

import com.beachape.aminam.app.authc.Principals;
import com.beachape.aminam.app.models.EmptyResponse;
import com.beachape.aminam.app.routes.v1.policies.models.PoliciesResponse;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyRequest;
import com.beachape.aminam.app.routes.v1.policies.models.PolicyResponse;
import com.beachape.aminam.domain.authz.models.PolicyId.CustomPolicyId;
import com.beachape.aminam.domain.authz.services.PolicyService;
import com.beachape.aminam.domain.authz.services.PolicyValidator.InvalidPolicyException;
import com.beachape.aminam.domain.errors.NotAuthorisedException;
import com.beachape.aminam.domain.errors.NotFoundException;
import com.beachape.aminam.domain.orgs.models.OrgId;
import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.resteasy.reactive.ResponseStatus;

/// Custom-policy authoring, mounted by OrgResource at `/orgs/{id}/policies`.
@ApplicationScoped
@RunOnVirtualThread
@Authenticated
public class OrgPoliciesResource {

  @Inject PolicyService policies;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ResponseStatus(201)
  @APIResponse(responseCode = "201")
  public PolicyResponse create(
      @PathParam("id") UUID orgId, @Valid PolicyRequest request, @Context SecurityContext security)
      throws NotFoundException, NotAuthorisedException, InvalidPolicyException {
    var user = Principals.requireUser(security);
    var created = policies.create(user, new OrgId(orgId), request.name(), request.toDomain());
    return PolicyResponse.from(created);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public PoliciesResponse list(@PathParam("id") UUID orgId, @Context SecurityContext security)
      throws NotFoundException {
    var user = Principals.requireUser(security);
    return new PoliciesResponse(
        policies.list(user, new OrgId(orgId)).stream().map(PolicyResponse::from).toList());
  }

  @GET
  @Path("{pid}")
  @Produces(MediaType.APPLICATION_JSON)
  public PolicyResponse get(
      @PathParam("id") UUID orgId, @PathParam("pid") UUID pid, @Context SecurityContext security)
      throws NotFoundException {
    var user = Principals.requireUser(security);
    return PolicyResponse.from(policies.get(user, new OrgId(orgId), new CustomPolicyId(pid)));
  }

  @PUT
  @Path("{pid}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public PolicyResponse update(
      @PathParam("id") UUID orgId,
      @PathParam("pid") UUID pid,
      @Valid PolicyRequest request,
      @Context SecurityContext security)
      throws NotFoundException, NotAuthorisedException, InvalidPolicyException {
    var user = Principals.requireUser(security);
    var updated =
        policies.update(
            user, new OrgId(orgId), new CustomPolicyId(pid), request.name(), request.toDomain());
    return PolicyResponse.from(updated);
  }

  @DELETE
  @Path("{pid}")
  @Produces(MediaType.APPLICATION_JSON)
  public EmptyResponse delete(
      @PathParam("id") UUID orgId, @PathParam("pid") UUID pid, @Context SecurityContext security)
      throws NotFoundException, NotAuthorisedException {
    var user = Principals.requireUser(security);
    policies.delete(user, new OrgId(orgId), new CustomPolicyId(pid));
    return new EmptyResponse();
  }
}
