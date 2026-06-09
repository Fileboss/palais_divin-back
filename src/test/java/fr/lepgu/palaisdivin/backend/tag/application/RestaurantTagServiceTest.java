package fr.lepgu.palaisdivin.backend.tag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import fr.lepgu.palaisdivin.backend.restaurant.domain.RestaurantNotFoundException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Coordinates;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.Restaurant;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.RestaurantRepositoryPort;
import fr.lepgu.palaisdivin.backend.shared.domain.ports.OutboxPublisher;
import fr.lepgu.palaisdivin.backend.tag.domain.TagNotFoundException;
import fr.lepgu.palaisdivin.backend.tag.domain.events.RestaurantTagAttached;
import fr.lepgu.palaisdivin.backend.tag.domain.events.RestaurantTagDetached;
import fr.lepgu.palaisdivin.backend.tag.domain.model.AttachResult;
import fr.lepgu.palaisdivin.backend.tag.domain.model.RestaurantTag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagCategory;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.RestaurantTagRepositoryPort;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.TagRepositoryPort;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RestaurantTagServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-03T12:00:00Z");
  private static final String SUBJECT = "kc-subject-xyz";

  @Mock RestaurantTagRepositoryPort restaurantTags;
  @Mock TagRepositoryPort tags;
  @Mock RestaurantRepositoryPort restaurants;
  @Mock UserRepositoryPort users;
  @Mock OutboxPublisher outbox;

  RestaurantTagService service;

  UserId attachedBy;
  RestaurantId restaurantId;
  TagId tagId;
  Tag tag;
  Restaurant restaurant;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    service = new RestaurantTagService(restaurantTags, tags, restaurants, users, outbox, clock);

    attachedBy = UserId.newId();
    restaurantId = RestaurantId.newId();
    tagId = TagId.newId();
    tag =
        new Tag(tagId, TagCategory.SPECIALTY, "natural-wine", "Natural wine", NOW.minusSeconds(60));
    restaurant =
        new Restaurant(
            restaurantId,
            "Septime",
            "80 Rue de Charonne",
            new Coordinates(48.85, 2.38),
            NOW.minusSeconds(60),
            null);
  }

  @Test
  void attach_persists_publishes_and_returns_created_true() {
    when(users.requireBySubject(SUBJECT)).thenReturn(attachedBy);
    when(restaurants.findById(restaurantId)).thenReturn(Optional.of(restaurant));
    when(tags.findById(tagId)).thenReturn(Optional.of(tag));
    when(restaurantTags.findByRestaurantAndTag(restaurantId, tagId)).thenReturn(Optional.empty());
    when(restaurantTags.save(any(RestaurantTag.class))).thenAnswer(inv -> inv.getArgument(0));

    AttachResult result = service.attach(SUBJECT, restaurantId, tagId);

    assertThat(result.created()).isTrue();
    assertThat(result.attachment().restaurantId()).isEqualTo(restaurantId);
    assertThat(result.attachment().tagId()).isEqualTo(tagId);
    assertThat(result.attachment().attachedBy()).isEqualTo(attachedBy);
    assertThat(result.attachment().attachedAt()).isEqualTo(NOW);

    ArgumentCaptor<RestaurantTagAttached> captor =
        ArgumentCaptor.forClass(RestaurantTagAttached.class);
    verify(outbox)
        .publish(
            eq("RestaurantTag"),
            eq(restaurantId.value()),
            eq("RestaurantTagAttached"),
            captor.capture());
    RestaurantTagAttached event = captor.getValue();
    assertThat(event.restaurantId()).isEqualTo(restaurantId.value());
    assertThat(event.tagId()).isEqualTo(tagId.value());
    assertThat(event.tagSlug()).isEqualTo("natural-wine");
    assertThat(event.tagCategory()).isEqualTo("SPECIALTY");
    assertThat(event.tagLabel()).isEqualTo("Natural wine");
    assertThat(event.attachedBy()).isEqualTo(attachedBy.value());
    assertThat(event.attachedAt()).isEqualTo(NOW);
  }

  @Test
  void attach_existing_pair_returns_created_false_no_publish() {
    RestaurantTag existing =
        new RestaurantTag(restaurantId, tagId, attachedBy, NOW.minusSeconds(3600));
    when(users.requireBySubject(SUBJECT)).thenReturn(attachedBy);
    when(restaurants.findById(restaurantId)).thenReturn(Optional.of(restaurant));
    when(tags.findById(tagId)).thenReturn(Optional.of(tag));
    when(restaurantTags.findByRestaurantAndTag(restaurantId, tagId))
        .thenReturn(Optional.of(existing));

    AttachResult result = service.attach(SUBJECT, restaurantId, tagId);

    assertThat(result.created()).isFalse();
    assertThat(result.attachment()).isEqualTo(existing);
    verify(restaurantTags, never()).save(any());
    verifyNoInteractions(outbox);
  }

  @Test
  void attach_unknown_restaurant_throws_RestaurantNotFoundException() {
    when(users.requireBySubject(SUBJECT)).thenReturn(attachedBy);
    when(restaurants.findById(restaurantId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.attach(SUBJECT, restaurantId, tagId))
        .isInstanceOf(RestaurantNotFoundException.class);

    verify(restaurantTags, never()).save(any());
    verifyNoInteractions(outbox);
  }

  @Test
  void attach_unknown_tag_throws_TagNotFoundException() {
    when(users.requireBySubject(SUBJECT)).thenReturn(attachedBy);
    when(restaurants.findById(restaurantId)).thenReturn(Optional.of(restaurant));
    when(tags.findById(tagId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.attach(SUBJECT, restaurantId, tagId))
        .isInstanceOf(TagNotFoundException.class);

    verify(restaurantTags, never()).save(any());
    verifyNoInteractions(outbox);
  }

  @Test
  void detach_existing_row_deletes_and_publishes() {
    when(restaurantTags.delete(restaurantId, tagId)).thenReturn(true);

    service.detach(restaurantId, tagId);

    ArgumentCaptor<RestaurantTagDetached> captor =
        ArgumentCaptor.forClass(RestaurantTagDetached.class);
    verify(outbox)
        .publish(
            eq("RestaurantTag"),
            eq(restaurantId.value()),
            eq("RestaurantTagDetached"),
            captor.capture());
    RestaurantTagDetached event = captor.getValue();
    assertThat(event.restaurantId()).isEqualTo(restaurantId.value());
    assertThat(event.tagId()).isEqualTo(tagId.value());
    assertThat(event.detachedAt()).isEqualTo(NOW);
  }

  @Test
  void detach_missing_row_no_publish() {
    when(restaurantTags.delete(restaurantId, tagId)).thenReturn(false);

    service.detach(restaurantId, tagId);

    verifyNoInteractions(outbox);
  }

  @Test
  void listFor_collection_delegates_to_repo_and_returns_grouped_map() {
    RestaurantId other = RestaurantId.newId();
    Map<RestaurantId, List<Tag>> expected = Map.of(restaurantId, List.of(tag));
    when(restaurantTags.findTagsByRestaurants(List.of(restaurantId, other))).thenReturn(expected);

    Map<RestaurantId, List<Tag>> got = service.listFor(List.of(restaurantId, other));

    assertThat(got).isSameAs(expected);
    verify(restaurantTags).findTagsByRestaurants(List.of(restaurantId, other));
  }

  @Test
  void listFor_empty_collection_short_circuits_no_query() {
    Map<RestaurantId, List<Tag>> got = service.listFor(List.of());

    assertThat(got).isEmpty();
    verify(restaurantTags, never()).findTagsByRestaurants(any());
  }
}
