package fr.lepgu.palaisdivin.backend.tag.adapters.postgres;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface TagJpaRepository extends JpaRepository<TagEntity, UUID> {

  List<TagEntity> findAllByOrderByCategoryAscSlugAsc();
}
