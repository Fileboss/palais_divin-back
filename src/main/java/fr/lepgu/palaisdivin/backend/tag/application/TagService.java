package fr.lepgu.palaisdivin.backend.tag.application;

import fr.lepgu.palaisdivin.backend.shared.domain.ports.OutboxPublisher;
import fr.lepgu.palaisdivin.backend.tag.domain.TagNotFoundException;
import fr.lepgu.palaisdivin.backend.tag.domain.events.TagDeleted;
import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagCategory;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.CreateTagUseCase;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.DeleteTagUseCase;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.ListTagsUseCase;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.TagRepositoryPort;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TagService implements CreateTagUseCase, ListTagsUseCase, DeleteTagUseCase {

  private static final String AGGREGATE_TYPE = "Tag";

  private final TagRepositoryPort tags;
  private final OutboxPublisher outbox;
  private final Clock clock;

  public TagService(TagRepositoryPort tags, OutboxPublisher outbox, Clock clock) {
    this.tags = tags;
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
        AGGREGATE_TYPE, id.value(), "TagDeleted", new TagDeleted(id.value(), clock.instant()));
  }
}
