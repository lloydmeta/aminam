package com.beachape.aminam.app.routes.v1.authz.models;

import com.beachape.aminam.domain.authz.models.AuthzDecision;

public record CheckResult(boolean ok, String reason) {

  static CheckResult from(AuthzDecision decision) {
    var reason =
        switch (decision) {
          case AuthzDecision.Allow allow -> allow.reason();
          case AuthzDecision.Deny deny -> deny.reason();
        };
    return new CheckResult(decision.allowed(), reason);
  }
}
