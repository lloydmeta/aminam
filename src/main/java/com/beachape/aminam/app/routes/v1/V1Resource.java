package com.beachape.aminam.app.routes.v1;

import com.beachape.aminam.app.routes.v1.authc.LoginResource;
import com.beachape.aminam.app.routes.v1.authc.LogoutResource;
import com.beachape.aminam.app.routes.v1.authc.MeResource;
import com.beachape.aminam.app.routes.v1.authc.SignupResource;
import com.beachape.aminam.app.routes.v1.authz.AuthzResource;
import com.beachape.aminam.app.routes.v1.databases.DatabaseResource;
import com.beachape.aminam.app.routes.v1.orgs.OrgResource;
import com.beachape.aminam.app.routes.v1.orgs.SessionResource;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;

@RunOnVirtualThread
@Path("/api/v1")
public class V1Resource {

  @Inject SignupResource signupResource;
  @Inject LoginResource loginResource;
  @Inject LogoutResource logoutResource;
  @Inject MeResource meResource;
  @Inject OrgResource orgResource;
  @Inject SessionResource sessionResource;
  @Inject DatabaseResource databaseResource;
  @Inject AuthzResource authzResource;

  @Path("/signup")
  public SignupResource signup() {
    return signupResource;
  }

  @Path("/login")
  public LoginResource login() {
    return loginResource;
  }

  @Path("/logout")
  public LogoutResource logout() {
    return logoutResource;
  }

  @Path("/me")
  public MeResource me() {
    return meResource;
  }

  @Path("/orgs")
  public OrgResource orgs() {
    return orgResource;
  }

  @Path("/sessions")
  public SessionResource sessions() {
    return sessionResource;
  }

  @Path("/databases")
  public DatabaseResource databases() {
    return databaseResource;
  }

  @Path("/authz")
  public AuthzResource authz() {
    return authzResource;
  }
}
