package fr.lepgu.palaisdivin.backend.tag.domain.ports;

import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagImplication;

public interface CreateTagImplicationUseCase {

  TagImplication create(TagId tagId, TagId impliesTagId);
}
