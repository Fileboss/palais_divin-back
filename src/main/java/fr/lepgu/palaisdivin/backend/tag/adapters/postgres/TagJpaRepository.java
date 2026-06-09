package fr.lepgu.palaisdivin.backend.tag.adapters.postgres;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TagJpaRepository extends JpaRepository<TagEntity, UUID> {

  List<TagEntity> findAllByOrderByCategoryAscSlugAsc();

  List<TagEntity> findAllByCategoryOrderBySlugAsc(String category);

  List<TagEntity> findBySlugIn(Collection<String> slugs);

  List<TagEntity> findByIdIn(Collection<UUID> ids);
}
