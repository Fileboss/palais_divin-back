package fr.lepgu.palaisdivin.backend.tag.domain.ports;

import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;

public interface DeleteTagUseCase {

  void delete(TagId id);
}
