package com.beachape.aminam.domain.authz.models;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.beachape.aminam.domain.authz.models.PolicyId.CustomPolicyId;
import com.beachape.aminam.domain.authz.models.PolicyId.MalformedPolicyIdException;
import com.beachape.aminam.domain.authz.models.PolicyId.SystemPolicyId;
import java.util.List;
import org.junit.jupiter.api.Test;

final class PolicyIdTest {

  @Test
  void systemIdRoundTripsThroughText() throws Exception {
    var id = new SystemPolicyId("system:admin");

    assertThat(id.asText()).isEqualTo("system:admin");
    assertThat(PolicyId.fromText("system:admin")).isEqualTo(id);
  }

  @Test
  void customIdRoundTripsThroughText() throws Exception {
    var uuid = randomUUID();
    var id = new CustomPolicyId(uuid);

    assertThat(id.asText()).isEqualTo(uuid.toString());
    assertThat(PolicyId.fromText(uuid.toString())).isEqualTo(id);
  }

  @Test
  void anyPrefixedStringParsesAsASystemId() throws Exception {
    assertThat(PolicyId.fromText("system:anything"))
        .isEqualTo(new SystemPolicyId("system:anything"));
  }

  @Test
  void fromTextThrowsCheckedOnAnUnparseableString() {
    assertThatExceptionOfType(MalformedPolicyIdException.class)
        .isThrownBy(() -> PolicyId.fromText("not-a-uuid-and-not-system"));
  }

  @Test
  void unsafeFromStoredTextRoundTripsBothArms() {
    var uuid = randomUUID();
    assertThat(PolicyId.unsafeFromStoredText("system:viewer"))
        .isEqualTo(new SystemPolicyId("system:viewer"));
    assertThat(PolicyId.unsafeFromStoredText(uuid.toString())).isEqualTo(new CustomPolicyId(uuid));
  }

  @Test
  void unsafeFromStoredTextThrowsIllegalStateOnCorruption() {
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> PolicyId.unsafeFromStoredText("not-a-uuid-and-not-system"));
  }

  @Test
  void fromRawParsesEachIdInOrder() throws Exception {
    var uuid = randomUUID();

    assertThat(PolicyId.fromRaw(List.of("system:admin", uuid.toString())))
        .containsExactly(new SystemPolicyId("system:admin"), new CustomPolicyId(uuid));
    assertThat(PolicyId.fromRaw(List.of())).isEmpty();
  }

  @Test
  void fromRawThrowsCheckedWhenAnyIdIsUnparseable() {
    assertThatExceptionOfType(MalformedPolicyIdException.class)
        .isThrownBy(() -> PolicyId.fromRaw(List.of("system:admin", "not-a-uuid-and-not-system")));
  }

  @Test
  void fromRawReportsEveryUnparseableId() {
    assertThatExceptionOfType(MalformedPolicyIdException.class)
        .isThrownBy(() -> PolicyId.fromRaw(List.of("bad-one", "system:admin", "bad-two")))
        .satisfies(
            e -> {
              assertThat(e.getMessage()).contains("bad-one").contains("bad-two");
            });
  }
}
