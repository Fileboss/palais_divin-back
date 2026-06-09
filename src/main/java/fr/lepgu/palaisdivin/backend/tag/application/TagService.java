package fr.lepgu.palaisdivin.backend.tag.application;

import fr.lepgu.palaisdivin.backend.shared.domain.ports.OutboxPublisher;
import fr.lepgu.palaisdivin.backend.tag.domain.TagImplicationNotFoundException;
import fr.lepgu.palaisdivin.backend.tag.domain.TagNotFoundException;
import fr.lepgu.palaisdivin.backend.tag.domain.events.TagDeleted;
import fr.lepgu.palaisdivin.backend.tag.domain.events.TagImplicationCreated;
import fr.lepgu.palaisdivin.backend.tag.domain.events.TagImplicationDeleted;
import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagCategory;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagImplication;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.CreateTagImplicationUseCase;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.CreateTagUseCase;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.DeleteTagImplicationUseCase;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.DeleteTagUseCase;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.ExpandTagSlugsUseCase;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.ListTagImplicationsUseCase;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.ListTagsUseCase;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.TagImplicationRepositoryPort;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.TagRepositoryPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TagService
    implements CreateTagUseCase,
        ListTagsUseCase,
        DeleteTagUseCase,
        CreateTagImplicationUseCase,
        DeleteTagImplicationUseCase,
        ListTagImplicationsUseCase,
        ExpandTagSlugsUseCase {

  private static final String TAG_AGGREGATE = "Tag";
  private static final String TAG_IMPLICATION_AGGREGATE = "TagImplication";

  private final TagRepositoryPort tags;
  private final TagImplicationRepositoryPort implications;
  private final OutboxPublisher outbox;
  private final Clock clock;

  public TagService(
      TagRepositoryPort tags,
      TagImplicationRepositoryPort implications,
      OutboxPublisher outbox,
      Clock clock) {
    this.tags = tags;
    this.implications = implications;
    this.outbox = outbox;
    this.clock = clock;
  }

  @Override
  public Tag create(TagCategory category, String slug, String label) {
    Tag tag = new Tag(TagId.newId(), category, slug, label, clock.instant());
    return tags.save(tag);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Tag> list() {
    return tags.findAll();
  }

  @Override
  public void delete(TagId id) {
    tags.findById(id).orElseThrow(() -> new TagNotFoundException(id));
    tags.deleteById(id);
    outbox.publish(
        TAG_AGGREGATE, id.value(), "TagDeleted", new TagDeleted(id.value(), clock.instant()));
  }

  @Override
  public TagImplication create(TagId tagId, TagId impliesTagId) {
    tags.findById(tagId).orElseThrow(() -> new TagNotFoundException(tagId));
    tags.findById(impliesTagId).orElseThrow(() -> new TagNotFoundException(impliesTagId));
    Instant now = clock.instant();
    TagImplication saved = implications.save(new TagImplication(tagId, impliesTagId, now));
    outbox.publish(
        TAG_IMPLICATION_AGGREGATE,
        tagId.value(),
        "TagImplicationCreated",
        new TagImplicationCreated(tagId.value(), impliesTagId.value(), now));
    return saved;
  }

  @Override
  public void delete(TagId tagId, TagId impliesTagId) {
    if (!implications.delete(tagId, impliesTagId)) {
      throw new TagImplicationNotFoundException(tagId, impliesTagId);
    }
    outbox.publish(
        TAG_IMPLICATION_AGGREGATE,
        tagId.value(),
        "TagImplicationDeleted",
        new TagImplicationDeleted(tagId.value(), impliesTagId.value(), clock.instant()));
  }

  @Override
  @Transactional(readOnly = true)
  public List<TagImplication> listAll() {
    return implications.findAll();
  }

  @Override
  @Transactional(readOnly = true)
  public Map<String, Set<String>> expand(Collection<String> slugs) {
    if (slugs.isEmpty()) {
      return Map.of();
    }
    Map<String, Set<String>> result = new LinkedHashMap<>();
    for (String slug : slugs) {
      result.put(slug, new HashSet<>(Set.of(slug)));
    }
    List<Tag> known = tags.findBySlugs(slugs);
    if (known.isEmpty()) {
      return result;
    }
    Map<UUID, String> slugByKnownId =
        known.stream().collect(Collectors.toMap(t -> t.id().value(), Tag::slug));

    List<TagId> knownIds = known.stream().map(Tag::id).toList();
    List<TagImplication> scoped = implications.findByImpliedIn(knownIds);
    if (scoped.isEmpty()) {
      return result;
    }

    Set<TagId> implicantIds =
        scoped.stream().map(TagImplication::tagId).collect(Collectors.toUnmodifiableSet());
    Map<UUID, String> slugByImplicantId =
        tags.findByIds(implicantIds).stream()
            .collect(Collectors.toMap(t -> t.id().value(), Tag::slug));

    for (TagImplication impl : scoped) {
      String impliedSlug = slugByKnownId.get(impl.impliesTagId().value());
      String implicantSlug = slugByImplicantId.get(impl.tagId().value());
      if (impliedSlug == null || implicantSlug == null) {
        continue;
      }
      result.get(impliedSlug).add(implicantSlug);
    }
    return result;
  }
}
