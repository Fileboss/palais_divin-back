package fr.lepgu.palaisdivin.backend.tag.application;

import fr.lepgu.palaisdivin.backend.restaurant.domain.RestaurantNotFoundException;
import fr.lepgu.palaisdivin.backend.restaurant.domain.model.RestaurantId;
import fr.lepgu.palaisdivin.backend.restaurant.domain.ports.RestaurantRepositoryPort;
import fr.lepgu.palaisdivin.backend.shared.domain.ports.OutboxPublisher;
import fr.lepgu.palaisdivin.backend.tag.domain.TagNotFoundException;
import fr.lepgu.palaisdivin.backend.tag.domain.events.RestaurantTagAttached;
import fr.lepgu.palaisdivin.backend.tag.domain.events.RestaurantTagDetached;
import fr.lepgu.palaisdivin.backend.tag.domain.model.AttachResult;
import fr.lepgu.palaisdivin.backend.tag.domain.model.RestaurantTag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.AttachTagUseCase;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.DetachTagUseCase;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.ListRestaurantTagsUseCase;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.RestaurantTagRepositoryPort;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.TagRepositoryPort;
import fr.lepgu.palaisdivin.backend.user.domain.model.UserId;
import fr.lepgu.palaisdivin.backend.user.domain.ports.UserRepositoryPort;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RestaurantTagService
    implements AttachTagUseCase, DetachTagUseCase, ListRestaurantTagsUseCase {

  private static final String AGGREGATE_TYPE = "RestaurantTag";

  private final RestaurantTagRepositoryPort restaurantTags;
  private final TagRepositoryPort tags;
  private final RestaurantRepositoryPort restaurants;
  private final UserRepositoryPort users;
  private final OutboxPublisher outbox;
  private final Clock clock;

  RestaurantTagService(
      RestaurantTagRepositoryPort restaurantTags,
      TagRepositoryPort tags,
      RestaurantRepositoryPort restaurants,
      UserRepositoryPort users,
      OutboxPublisher outbox,
      Clock clock) {
    this.restaurantTags = restaurantTags;
    this.tags = tags;
    this.restaurants = restaurants;
    this.users = users;
    this.outbox = outbox;
    this.clock = clock;
  }

  @Override
  public AttachResult attach(String subject, RestaurantId restaurantId, TagId tagId) {
    UserId attachedBy = users.requireBySubject(subject);
    restaurants
        .findById(restaurantId)
        .orElseThrow(() -> new RestaurantNotFoundException(restaurantId));
    Tag tag = tags.findById(tagId).orElseThrow(() -> new TagNotFoundException(tagId));

    Optional<RestaurantTag> existing = restaurantTags.findByRestaurantAndTag(restaurantId, tagId);
    if (existing.isPresent()) {
      return new AttachResult(existing.get(), false);
    }

    RestaurantTag saved =
        restaurantTags.save(new RestaurantTag(restaurantId, tagId, attachedBy, clock.instant()));

    outbox.publish(
        AGGREGATE_TYPE,
        restaurantId.value(),
        "RestaurantTagAttached",
        new RestaurantTagAttached(
            restaurantId.value(),
            tagId.value(),
            tag.slug(),
            tag.category().name(),
            tag.label(),
            attachedBy.value(),
            saved.attachedAt()));
    return new AttachResult(saved, true);
  }

  @Override
  public void detach(RestaurantId restaurantId, TagId tagId) {
    if (restaurantTags.delete(restaurantId, tagId)) {
      outbox.publish(
          AGGREGATE_TYPE,
          restaurantId.value(),
          "RestaurantTagDetached",
          new RestaurantTagDetached(restaurantId.value(), tagId.value(), clock.instant()));
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<Tag> listFor(RestaurantId restaurantId) {
    return restaurantTags.findTagsByRestaurant(restaurantId);
  }
}
