package com.beachape.aminam.domain.authz.models;

import static com.beachape.aminam.domain.authz.models.ResourceType.DATABASE;
import static com.beachape.aminam.domain.authz.models.ResourceType.ORG;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import com.beachape.aminam.domain.orgs.models.OrgId;
import org.junit.jupiter.api.Test;

final class ResourcePatternTest {

  @Test
  void wildcardMatchesAnyIdOfItsType() {
    assertThat(
            ResourcePattern.wildcard(DATABASE)
                .matches(new ResourceRef.Existing(DATABASE, randomUUID())))
        .isTrue();
  }

  @Test
  void wildcardDoesNotMatchAnotherType() {
    assertThat(
            ResourcePattern.wildcard(ORG).matches(new ResourceRef.Existing(DATABASE, randomUUID())))
        .isFalse();
  }

  @Test
  void concreteIdMatchesTheSameId() {
    var id = randomUUID();
    assertThat(new ResourcePattern(DATABASE, id).matches(new ResourceRef.Existing(DATABASE, id)))
        .isTrue();
  }

  @Test
  void concreteIdDoesNotMatchADifferentId() {
    assertThat(
            new ResourcePattern(DATABASE, randomUUID())
                .matches(new ResourceRef.Existing(DATABASE, randomUUID())))
        .isFalse();
  }

  @Test
  void concreteIdDoesNotMatchADifferentType() {
    var id = randomUUID();
    assertThat(new ResourcePattern(DATABASE, id).matches(new ResourceRef.Existing(ORG, id)))
        .isFalse();
  }

  @Test
  void wildcardMatchesAToCreateOfItsType() {
    assertThat(
            ResourcePattern.wildcard(DATABASE)
                .matches(new ResourceRef.ToCreate(DATABASE, new OrgId(randomUUID()))))
        .isTrue();
  }

  @Test
  void concreteIdNeverMatchesAToCreate() {
    assertThat(
            new ResourcePattern(DATABASE, randomUUID())
                .matches(new ResourceRef.ToCreate(DATABASE, new OrgId(randomUUID()))))
        .isFalse();
  }
}
