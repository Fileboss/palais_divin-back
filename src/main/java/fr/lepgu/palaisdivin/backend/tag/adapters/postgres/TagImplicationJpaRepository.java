package fr.lepgu.palaisdivin.backend.tag.adapters.postgres;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TagImplicationJpaRepository
    extends JpaRepository<TagImplicationEntity, TagImplicationPk> {

  List<TagImplicationEntity> findAllByOrderByTagIdAscImpliesTagIdAsc();

  @Query("select e.tagId from TagImplicationEntity e where e.impliesTagId in :impliedIds")
  List<UUID> findTagIdsImplying(@Param("impliedIds") Collection<UUID> impliedIds);

  List<TagImplicationEntity> findByImpliesTagIdIn(Collection<UUID> impliedIds);
}
