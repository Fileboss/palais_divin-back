package fr.lepgu.palaisdivin.backend.tag.domain.ports;

import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import java.util.List;
import java.util.Optional;

public interface TagRepositoryPort {

  Tag save(Tag tag);

  Optional<Tag> findById(TagId id);

  List<Tag> findAll();
}
