package fr.lepgu.palaisdivin.backend.tag.domain.ports;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface ExpandTagSlugsUseCase {

  Map<String, Set<String>> expand(Collection<String> slugs);
}
