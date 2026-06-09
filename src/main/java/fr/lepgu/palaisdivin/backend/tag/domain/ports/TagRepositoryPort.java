package fr.lepgu.palaisdivin.backend.tag.domain.ports;

import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagCategory;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TagRepositoryPort {

  Tag save(Tag tag);

  Optional<Tag> findById(TagId id);

  List<Tag> findAll();

  List<Tag> findAllByCategory(TagCategory category);

  List<Tag> findBySlugs(Collection<String> slugs);

  List<Tag> findByIds(Collection<TagId> ids);

  void deleteById(TagId id);
}
