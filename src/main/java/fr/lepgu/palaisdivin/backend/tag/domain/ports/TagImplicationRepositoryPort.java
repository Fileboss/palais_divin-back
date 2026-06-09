package fr.lepgu.palaisdivin.backend.tag.domain.ports;

import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagImplication;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface TagImplicationRepositoryPort {

  TagImplication save(TagImplication implication);

  boolean delete(TagId tagId, TagId impliesTagId);

  List<TagImplication> findAll();

  Set<TagId> findImplicantsOf(Collection<TagId> impliedIds);

  List<TagImplication> findByImpliedIn(Collection<TagId> impliedIds);
}
