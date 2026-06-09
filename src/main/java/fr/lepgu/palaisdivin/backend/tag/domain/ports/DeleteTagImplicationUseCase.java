package fr.lepgu.palaisdivin.backend.tag.domain.ports;

import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;

public interface DeleteTagImplicationUseCase {

  void delete(TagId tagId, TagId impliesTagId);
}
