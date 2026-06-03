package fr.lepgu.palaisdivin.backend.tag.application;

import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagCategory;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.CreateTagUseCase;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.ListTagsUseCase;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.TagRepositoryPort;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TagService implements CreateTagUseCase, ListTagsUseCase {

  private final TagRepositoryPort tags;
  private final Clock clock;

  public TagService(TagRepositoryPort tags, Clock clock) {
    this.tags = tags;
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
}
