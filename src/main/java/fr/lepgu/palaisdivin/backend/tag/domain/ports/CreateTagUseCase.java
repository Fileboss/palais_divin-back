package fr.lepgu.palaisdivin.backend.tag.domain.ports;

import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagCategory;
import java.util.Map;

public interface CreateTagUseCase {
  Tag create(TagCategory category, String slug, String label, Map<String, String> labelI18n);
}
