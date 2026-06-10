package fr.lepgu.palaisdivin.backend.tag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.lepgu.palaisdivin.backend.shared.domain.ports.OutboxPublisher;
import fr.lepgu.palaisdivin.backend.tag.domain.TagImplicationNotFoundException;
import fr.lepgu.palaisdivin.backend.tag.domain.TagNotFoundException;
import fr.lepgu.palaisdivin.backend.tag.domain.events.TagDeleted;
import fr.lepgu.palaisdivin.backend.tag.domain.events.TagImplicationCreated;
import fr.lepgu.palaisdivin.backend.tag.domain.events.TagImplicationDeleted;
import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagCategory;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagImplication;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.TagImplicationRepositoryPort;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.TagRepositoryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {

  private static final Instant NOW = Instant.parse("2026-06-03T12:00:00Z");

  @Mock TagRepositoryPort tags;
  @Mock TagImplicationRepositoryPort implications;
  @Mock OutboxPublisher outbox;

  TagService service;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    service = new TagService(tags, implications, outbox, clock);
  }

  @Test
  void create_persists_with_generated_id_and_clock_instant() {
    when(tags.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

    Tag result = service.create(TagCategory.SPECIALTY, "natural-wine", "Natural wine", Map.of());

    ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
    verify(tags).save(captor.capture());
    Tag persisted = captor.getValue();
    assertThat(persisted.id()).isNotNull();
    assertThat(persisted.id().value()).isNotNull();
    assertThat(persisted.category()).isEqualTo(TagCategory.SPECIALTY);
    assertThat(persisted.slug()).isEqualTo("natural-wine");
    assertThat(persisted.label()).isEqualTo("Natural wine");
    assertThat(persisted.labelI18n()).isEmpty();
    assertThat(persisted.createdAt()).isEqualTo(NOW);
    assertThat(result).isEqualTo(persisted);
  }

  @Test
  void create_persists_labelI18n_when_provided() {
    when(tags.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

    Tag result =
        service.create(
            TagCategory.REGIME,
            "vegan",
            "Végétalien",
            Map.of("en", "Vegan", "es", "Vegano", "de", "Vegan"));

    ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
    verify(tags).save(captor.capture());
    assertThat(captor.getValue().labelI18n())
        .containsEntry("en", "Vegan")
        .containsEntry("es", "Vegano")
        .containsEntry("de", "Vegan");
    assertThat(result.labelI18n()).hasSize(3);
  }

  @Test
  void create_propagates_repository_failure() {
    when(tags.save(any(Tag.class))).thenThrow(new DataIntegrityViolationException("uq_tag_slug"));

    assertThatThrownBy(
            () -> service.create(TagCategory.SPECIALTY, "natural-wine", "Natural wine", Map.of()))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uq_tag_slug");
  }

  @Test
  void list_delegates_to_repository() {
    Tag a =
        new Tag(
            TagId.newId(), TagCategory.SPECIALTY, "natural-wine", "Natural wine", Map.of(), NOW);
    Tag b = new Tag(TagId.newId(), TagCategory.REGIME, "vegan", "Vegan", Map.of(), NOW);
    when(tags.findAll()).thenReturn(List.of(a, b));

    List<Tag> result = service.list();

    assertThat(result).containsExactly(a, b);
    verify(tags).findAll();
  }

  @Test
  void list_byCategory_delegates_to_repository() {
    Tag vegan = new Tag(TagId.newId(), TagCategory.REGIME, "vegan", "Vegan", Map.of(), NOW);
    when(tags.findAllByCategory(TagCategory.REGIME)).thenReturn(List.of(vegan));

    List<Tag> result = service.list(TagCategory.REGIME);

    assertThat(result).containsExactly(vegan);
    verify(tags).findAllByCategory(TagCategory.REGIME);
    verify(tags, never()).findAll();
  }

  @Test
  void delete_persists_then_publishes_TagDeleted_event() {
    TagId id = TagId.newId();
    Tag existing =
        new Tag(id, TagCategory.SPECIALTY, "natural-wine", "Natural wine", Map.of(), NOW);
    when(tags.findById(id)).thenReturn(Optional.of(existing));

    service.delete(id);

    verify(tags).deleteById(id);
    verify(outbox)
        .publish(eq("Tag"), eq(id.value()), eq("TagDeleted"), eq(new TagDeleted(id.value(), NOW)));
  }

  @Test
  void delete_throws_TagNotFound_when_missing_and_does_not_publish() {
    TagId id = TagId.newId();
    when(tags.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(id)).isInstanceOf(TagNotFoundException.class);

    verify(tags, never()).deleteById(any());
    verify(outbox, never()).publish(any(), any(), any(), any());
  }

  @Test
  void createImplication_persists_and_publishes_event() {
    TagId src = TagId.newId();
    TagId dst = TagId.newId();
    Tag srcTag = new Tag(src, TagCategory.REGIME, "vegan-100", "100% vegan", Map.of(), NOW);
    Tag dstTag = new Tag(dst, TagCategory.REGIME, "vegan-option", "Vegan option", Map.of(), NOW);
    when(tags.findById(src)).thenReturn(Optional.of(srcTag));
    when(tags.findById(dst)).thenReturn(Optional.of(dstTag));
    when(implications.save(any(TagImplication.class))).thenAnswer(inv -> inv.getArgument(0));

    TagImplication created = service.create(src, dst);

    assertThat(created.tagId()).isEqualTo(src);
    assertThat(created.impliesTagId()).isEqualTo(dst);
    assertThat(created.createdAt()).isEqualTo(NOW);
    verify(outbox)
        .publish(
            eq("TagImplication"),
            eq(src.value()),
            eq("TagImplicationCreated"),
            eq(new TagImplicationCreated(src.value(), dst.value(), NOW)));
  }

  @Test
  void createImplication_throws_TagNotFound_when_tagId_missing() {
    TagId src = TagId.newId();
    TagId dst = TagId.newId();
    when(tags.findById(src)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(src, dst)).isInstanceOf(TagNotFoundException.class);
    verify(implications, never()).save(any());
    verify(outbox, never()).publish(any(), any(), any(), any());
  }

  @Test
  void deleteImplication_publishes_event_when_row_existed() {
    TagId src = TagId.newId();
    TagId dst = TagId.newId();
    when(implications.delete(src, dst)).thenReturn(true);

    service.delete(src, dst);

    verify(outbox)
        .publish(
            eq("TagImplication"),
            eq(src.value()),
            eq("TagImplicationDeleted"),
            eq(new TagImplicationDeleted(src.value(), dst.value(), NOW)));
  }

  @Test
  void deleteImplication_throws_when_row_absent() {
    TagId src = TagId.newId();
    TagId dst = TagId.newId();
    when(implications.delete(src, dst)).thenReturn(false);

    assertThatThrownBy(() -> service.delete(src, dst))
        .isInstanceOf(TagImplicationNotFoundException.class);
    verify(outbox, never()).publish(any(), any(), any(), any());
  }

  @Test
  void expand_returns_input_slugs_when_no_known_tags() {
    when(tags.findBySlugs(List.of("nope"))).thenReturn(List.of());

    Map<String, Set<String>> result = service.expand(List.of("nope"));

    assertThat(result).containsEntry("nope", Set.of("nope"));
  }

  @Test
  void expand_includes_implicant_slugs() {
    TagId optionId = TagId.newId();
    TagId vegan100Id = TagId.newId();
    Tag option =
        new Tag(optionId, TagCategory.REGIME, "vegan-option", "Vegan option", Map.of(), NOW);
    Tag vegan100 =
        new Tag(vegan100Id, TagCategory.REGIME, "vegan-100", "100% vegan", Map.of(), NOW);
    when(tags.findBySlugs(List.of("vegan-option"))).thenReturn(List.of(option));
    when(implications.findByImpliedIn(List.of(optionId)))
        .thenReturn(List.of(new TagImplication(vegan100Id, optionId, NOW)));
    when(tags.findByIds(Set.of(vegan100Id))).thenReturn(List.of(vegan100));

    Map<String, Set<String>> result = service.expand(List.of("vegan-option"));

    assertThat(result).containsKey("vegan-option");
    assertThat(result.get("vegan-option")).containsExactlyInAnyOrder("vegan-option", "vegan-100");
  }

  @Test
  void expand_empty_input_returns_empty_map() {
    Map<String, Set<String>> result = service.expand(List.of());
    assertThat(result).isEmpty();
  }
}
