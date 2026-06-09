package fr.lepgu.palaisdivin.backend.tag.adapters.postgres;

import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagCategory;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.TagRepositoryPort;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class TagPostgresAdapter implements TagRepositoryPort {

  private final TagJpaRepository jpa;

  TagPostgresAdapter(TagJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public Tag save(Tag tag) {
    return toDomain(jpa.save(toEntity(tag)));
  }

  @Override
  public Optional<Tag> findById(TagId id) {
    return jpa.findById(id.value()).map(TagPostgresAdapter::toDomain);
  }

  @Override
  public List<Tag> findAll() {
    return jpa.findAllByOrderByCategoryAscSlugAsc().stream()
        .map(TagPostgresAdapter::toDomain)
        .toList();
  }

  @Override
  public List<Tag> findBySlugs(Collection<String> slugs) {
    if (slugs.isEmpty()) {
      return List.of();
    }
    return jpa.findBySlugIn(slugs).stream().map(TagPostgresAdapter::toDomain).toList();
  }

  @Override
  public List<Tag> findByIds(Collection<TagId> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return jpa.findByIdIn(ids.stream().map(TagId::value).toList()).stream()
        .map(TagPostgresAdapter::toDomain)
        .toList();
  }

  @Override
  public void deleteById(TagId id) {
    jpa.deleteById(id.value());
  }

  private static TagEntity toEntity(Tag t) {
    return new TagEntity(t.id().value(), t.category().name(), t.slug(), t.label(), t.createdAt());
  }

  static Tag toDomain(TagEntity e) {
    return new Tag(
        new TagId(e.getId()),
        TagCategory.valueOf(e.getCategory()),
        e.getSlug(),
        e.getLabel(),
        e.getCreatedAt());
  }
}
