package fr.lepgu.palaisdivin.backend.tag.adapters.postgres;

import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagImplication;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.TagImplicationRepositoryPort;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class TagImplicationPostgresAdapter implements TagImplicationRepositoryPort {

  private final TagImplicationJpaRepository jpa;

  TagImplicationPostgresAdapter(TagImplicationJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public TagImplication save(TagImplication implication) {
    TagImplicationEntity saved =
        jpa.save(
            new TagImplicationEntity(
                implication.tagId().value(),
                implication.impliesTagId().value(),
                implication.createdAt()));
    return toDomain(saved);
  }

  @Override
  public boolean delete(TagId tagId, TagId impliesTagId) {
    TagImplicationPk pk = new TagImplicationPk(tagId.value(), impliesTagId.value());
    if (!jpa.existsById(pk)) {
      return false;
    }
    jpa.deleteById(pk);
    return true;
  }

  @Override
  public List<TagImplication> findAll() {
    return jpa.findAllByOrderByTagIdAscImpliesTagIdAsc().stream()
        .map(TagImplicationPostgresAdapter::toDomain)
        .toList();
  }

  @Override
  public Set<TagId> findImplicantsOf(Collection<TagId> impliedIds) {
    if (impliedIds.isEmpty()) {
      return Set.of();
    }
    List<java.util.UUID> raw = impliedIds.stream().map(TagId::value).toList();
    return jpa.findTagIdsImplying(raw).stream()
        .map(TagId::new)
        .collect(Collectors.toUnmodifiableSet());
  }

  @Override
  public List<TagImplication> findByImpliedIn(Collection<TagId> impliedIds) {
    if (impliedIds.isEmpty()) {
      return List.of();
    }
    List<java.util.UUID> raw = impliedIds.stream().map(TagId::value).toList();
    return jpa.findByImpliesTagIdIn(raw).stream()
        .map(TagImplicationPostgresAdapter::toDomain)
        .toList();
  }

  private static TagImplication toDomain(TagImplicationEntity e) {
    return new TagImplication(
        new TagId(e.getTagId()), new TagId(e.getImpliesTagId()), e.getCreatedAt());
  }
}
