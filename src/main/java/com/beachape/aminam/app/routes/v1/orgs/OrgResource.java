package com.beachape.aminam.app.routes.v1.orgs;

import static com.beachape.aminam.domain.authc.models.User.USERNAME_PATTERN;

import com.beachape.aminam.app.authc.Principals;
import com.beachape.aminam.app.models.EmptyResponse;
import com.beachape.aminam.app.routes.v1.databases.OrgDatabasesResource;
import com.beachape.aminam.app.routes.v1.orgs.models.AddMemberRequest;
import com.beachape.aminam.app.routes.v1.orgs.models.CreateOrgRequest;
import com.beachape.aminam.app.routes.v1.orgs.models.MemberResponse;
import com.beachape.aminam.app.routes.v1.orgs.models.MembersResponse;
import com.beachape.aminam.app.routes.v1.orgs.models.OrgResponse;
import com.beachape.aminam.app.routes.v1.orgs.models.OrgsResponse;
import com.beachape.aminam.app.routes.v1.orgs.models.PolicyIdsRequest;
import com.beachape.aminam.app.routes.v1.policies.OrgPoliciesResource;
import com.beachape.aminam.domain.authz.models.PolicyId;
import com.beachape.aminam.domain.authz.models.PolicyId.MalformedPolicyIdException;
import com.beachape.aminam.domain.authz.services.PolicyAuthzService.InvalidPoliciesException;
import com.beachape.aminam.domain.errors.NotAuthorisedException;
import com.beachape.aminam.domain.errors.NotFoundException;
import com.beachape.aminam.domain.orgs.models.OrgId;
import com.beachape.aminam.domain.orgs.services.OrganisationService;
import com.beachape.aminam.domain.orgs.services.OrganisationService.MemberAlreadyExistsException;
import io.quarkus.security.Authenticated;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

@ApplicationScoped
@RunOnVirtualThread
@Authenticated
public class OrgResource {

  @Inject OrganisationService organisations;
  @Inject OrgDatabasesResource orgDatabasesResource;
  @Inject OrgPoliciesResource orgPoliciesResource;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ResponseStatus(201)
  @APIResponse(responseCode = "201")
  public OrgResponse create(@Valid CreateOrgRequest request, @Context SecurityContext security) {
    var user = Principals.requireUser(security);
    var org = organisations.create(user.id(), request.name());
    return OrgResponse.from(org);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public OrgsResponse list(@Context SecurityContext security) {
    var user = Principals.requireUser(security);
    return new OrgsResponse(organisations.list(user.id()).stream().map(OrgResponse::from).toList());
  }

  @GET
  @Path("{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public OrgResponse get(@PathParam("id") UUID id, @Context SecurityContext security)
      throws NotFoundException {
    var user = Principals.requireUser(security);
    return OrgResponse.from(organisations.get(user, new OrgId(id)));
  }

  @GET
  @Path("{id}/members")
  @Produces(MediaType.APPLICATION_JSON)
  public MembersResponse members(@PathParam("id") UUID id, @Context SecurityContext security)
      throws NotFoundException {
    var actor = Principals.requireUser(security);
    return new MembersResponse(
        organisations.roster(actor, new OrgId(id)).stream().map(MemberResponse::from).toList());
  }

  @POST
  @Path("{id}/members")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @ResponseStatus(201)
  @APIResponse(responseCode = "201")
  public MemberResponse addMember(
      @PathParam("id") UUID id, @Valid AddMemberRequest request, @Context SecurityContext security)
      throws NotFoundException,
          NotAuthorisedException,
          InvalidPoliciesException,
          MemberAlreadyExistsException,
          MalformedPolicyIdException {
    var actor = Principals.requireUser(security);
    var member =
        organisations.addMember(
            actor, new OrgId(id), request.username(), PolicyId.fromRaw(request.policyIds()));
    return MemberResponse.from(member);
  }

  @PUT
  @Path("{id}/members/{username}/policies")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public MemberResponse setPolicies(
      @PathParam("id") UUID id,
      @PathParam("username") @Size(max = 255) @Pattern(regexp = USERNAME_PATTERN) String username,
      @Valid PolicyIdsRequest request,
      @Context SecurityContext security)
      throws NotFoundException,
          NotAuthorisedException,
          InvalidPoliciesException,
          MalformedPolicyIdException {
    var actor = Principals.requireUser(security);
    var member =
        organisations.setMemberPolicies(
            actor, new OrgId(id), username, PolicyId.fromRaw(request.policyIds()));
    return MemberResponse.from(member);
  }

  @DELETE
  @Path("{id}/members/{username}")
  @Produces(MediaType.APPLICATION_JSON)
  public EmptyResponse removeMember(
      @PathParam("id") UUID id,
      @PathParam("username") @Size(max = 255) @Pattern(regexp = USERNAME_PATTERN) String username,
      @Context SecurityContext security)
      throws NotAuthorisedException, NotFoundException {
    var actor = Principals.requireUser(security);
    organisations.removeMember(actor, new OrgId(id), username);
    return new EmptyResponse();
  }

  @DELETE
  @Path("{id}/membership")
  @Produces(MediaType.APPLICATION_JSON)
  public EmptyResponse leave(@PathParam("id") UUID id, @Context SecurityContext security)
      throws NotFoundException, NotAuthorisedException {
    var actor = Principals.requireUser(security);
    organisations.leave(actor, new OrgId(id));
    return new EmptyResponse();
  }

  @Path("{id}/databases")
  public OrgDatabasesResource databases() {
    return orgDatabasesResource;
  }

  @Path("{id}/policies")
  public OrgPoliciesResource policies() {
    return orgPoliciesResource;
  }
}
