package com.beachape.aminam.domain.authz.models;

import com.beachape.aminam.domain.errors.DomainException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Identifies a policy on an attachment: either a reserved system policy ("system:admin") or a
/// custom policy's UUID. A sealed pair so callers pattern-match instead of re-parsing a string.
public sealed interface PolicyId permits PolicyId.SystemPolicyId, PolicyId.CustomPolicyId {

  String SYSTEM_PREFIX = "system:";

  /// The canonical stored/serialised form: "system:<name>" or the UUID as text.
  String asText();

  record SystemPolicyId(String value) implements PolicyId {
    @Override
    public String asText() {
      return value;
    }
  }

  record CustomPolicyId(UUID value) implements PolicyId {
    @Override
    public String asText() {
      return value.toString();
    }
  }

  final class MalformedPolicyIdException extends DomainException {

    private final @Nullable String rawId;
    private final String message;

    @Override
    public String getMessage() {
      return message;
    }

    public MalformedPolicyIdException(String rawId, Throwable cause) {
      this.rawId = rawId;
      var message = "malformed policy id: " + rawId;
      this.message = message;
      super(message, cause);
    }

    public MalformedPolicyIdException(List<MalformedPolicyIdException> errors) {
      rawId = null;
      var message = messageFor(errors);
      this.message = message;
      super(message);
    }

    private static String messageFor(List<MalformedPolicyIdException> errors) {
      var rawIdParts = new ArrayList<String>(errors.size());
      for (var error : errors) {
        rawIdParts.add(error.rawId);
      }
      return "malformed policy ids: [" + String.join(", ", rawIdParts) + "]";
    }
  }

  static PolicyId fromText(String text) throws MalformedPolicyIdException {
    if (text.startsWith(SYSTEM_PREFIX)) {
      return new SystemPolicyId(text);
    }
    try {
      return new CustomPolicyId(UUID.fromString(text));
    } catch (IllegalArgumentException e) {
      throw new MalformedPolicyIdException(text, e);
    }
  }

  static PolicyId unsafeFromStoredText(String text) {
    try {
      return fromText(text);
    } catch (MalformedPolicyIdException e) {
      throw new IllegalStateException("unparseable stored policy id: " + text, e);
    }
  }

  static List<PolicyId> fromRaw(List<String> raw) throws MalformedPolicyIdException {
    var ids = new ArrayList<PolicyId>(raw.size());
    var errors = new ArrayList<MalformedPolicyIdException>();
    for (var value : raw) {
      try {
        ids.add(fromText(value));
      } catch (MalformedPolicyIdException e) {
        errors.add(e);
      }
    }
    if (!errors.isEmpty()) {
      throw new MalformedPolicyIdException(errors);
    }
    return ids;
  }
}
