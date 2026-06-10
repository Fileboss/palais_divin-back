package fr.lepgu.palaisdivin.backend.user.adapters.rest;

import static org.assertj.core.api.Assertions.assertThat;

import fr.lepgu.palaisdivin.backend.user.domain.model.User;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PublicUserResponseTest {

  private static final Instant FIXED_CREATED_AT = Instant.parse("2026-05-27T10:15:30Z");

  @Test
  void from_mapsAllFields_legacyFactorySetsFollowedNull() {
    UserId id = UserId.newId();
    User user = new User(id, "kc-subj", "alice@example.test", "Alice", FIXED_CREATED_AT);

    PublicUserResponse response = PublicUserResponse.from(user);

    assertThat(response.id()).isEqualTo(id.value());
    assertThat(response.displayName()).isEqualTo("Alice");
    assertThat(response.createdAt()).isEqualTo(FIXED_CREATED_AT);
    assertThat(response.isFollowedByMe()).isNull();
  }

  @Test
  void from_withFollowedFlag_roundTrips() {
    UserId id = UserId.newId();
    User user = new User(id, "kc-subj", "alice@example.test", "Alice", FIXED_CREATED_AT);

    assertThat(PublicUserResponse.from(user, Boolean.TRUE).isFollowedByMe()).isTrue();
    assertThat(PublicUserResponse.from(user, Boolean.FALSE).isFollowedByMe()).isFalse();
    assertThat(PublicUserResponse.from(user, null).isFollowedByMe()).isNull();
  }

  @Test
  void responseShape_exposesOnlyFourComponents_neverSubjectOrEmail() {
    Set<String> names =
        Stream.of(PublicUserResponse.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    assertThat(names).containsExactlyInAnyOrder("id", "displayName", "createdAt", "isFollowedByMe");
  }
}
