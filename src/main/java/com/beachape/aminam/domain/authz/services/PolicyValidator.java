package com.beachape.aminam.domain.authz.services;

import com.beachape.aminam.domain.authz.models.PolicyDocument;
import com.beachape.aminam.domain.errors.DomainException;
import java.util.List;

/// Validates a custom policy document before it is stored: structural checks and CEL condition
/// compilation. The CEL implementation lives in infra.
public interface PolicyValidator {

  void validate(PolicyDocument document) throws InvalidPolicyException;

  /// One or more parts of a document failed validation; carries a failure per problem so the whole
  /// request fails atomically with the full list.
  final class InvalidPolicyException extends DomainException {

    public record Failure(String location, String reason) {}

    private final List<Failure> failures;

    public InvalidPolicyException(List<Failure> failures) {
      super("invalid policy: " + failures);
      this.failures = List.copyOf(failures);
    }

    public List<Failure> failures() {
      return failures;
    }
  }
}
