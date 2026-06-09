package fr.lepgu.palaisdivin.backend.tag.domain.ports;

import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagCategory;
import java.util.List;

public interface ListTagsUseCase {
  List<Tag> list();

  List<Tag> list(TagCategory category);
}
