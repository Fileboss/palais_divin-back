package fr.lepgu.palaisdivin.backend.tag.domain.ports;

import fr.lepgu.palaisdivin.backend.tag.domain.model.TagImplication;
import java.util.List;

public interface ListTagImplicationsUseCase {

  List<TagImplication> listAll();
}
