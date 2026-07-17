package com.beachape.aminam.infra.authz.services;

import com.beachape.aminam.domain.authz.models.PolicyDocument;
import com.beachape.aminam.domain.authz.services.PolicyValidator;
import dev.cel.compiler.CelCompiler;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;

/// CEL-backed PolicyValidator: structural checks plus compiling and type-checking each statement's
/// condition against the declared environment. Failures accumulate so one request reports them all.
@ApplicationScoped
class CelPolicyValidator implements PolicyValidator {

  private final CelCompiler compiler = CelPolicyEnvironment.compiler();

  @Override
  public void validate(PolicyDocument document) throws InvalidPolicyException {
    var failures = new ArrayList<InvalidPolicyException.Failure>();
    var statements = document.statements();
    if (statements.isEmpty()) {
      failures.add(new InvalidPolicyException.Failure("statements", "must not be empty"));
    }
    for (int i = 0; i < statements.size(); i++) {
      var statement = statements.get(i);
      var at = "statements[" + i + "]";
      if (statement.actions().isEmpty()) {
        failures.add(new InvalidPolicyException.Failure(at + ".actions", "must not be empty"));
      }
      if (statement.resources().isEmpty()) {
        failures.add(new InvalidPolicyException.Failure(at + ".resources", "must not be empty"));
      }
      var condition = statement.condition();
      if (condition != null) {
        var result = compiler.compile(condition);
        if (result.hasError()) {
          failures.add(
              new InvalidPolicyException.Failure(at + ".condition", result.getErrorString()));
        }
      }
    }
    if (!failures.isEmpty()) {
      throw new InvalidPolicyException(failures);
    }
  }
}
