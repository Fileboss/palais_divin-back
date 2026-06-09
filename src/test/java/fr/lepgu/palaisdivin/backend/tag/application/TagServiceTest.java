package fr.lepgu.palaisdivin.backend.tag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import fr.lepgu.palaisdivin.backend.shared.domain.ports.OutboxPublisher;
import fr.lepgu.palaisdivin.backend.tag.domain.TagNotFoundException;
import fr.lepgu.palaisdivin.backend.tag.domain.events.TagDeleted;
import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagCategory;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.TagRepositoryPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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
  @Mock OutboxPublisher outbox;

  TagService service;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    service = new TagService(tags, outbox, clock);
  }

  @Test
  void create_persists_with_generated_id_and_clock_instant() {
    when(tags.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

    Tag result = service.create(TagCategory.FOOD, "natural-wine", "Natural wine");

    ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
    verify(tags).save(captor.capture());
    Tag persisted = captor.getValue();
    assertThat(persisted.id()).isNotNull();
    assertThat(persisted.id().value()).isNotNull();
    assertThat(persisted.category()).isEqualTo(TagCategory.FOOD);
    assertThat(persisted.slug()).isEqualTo("natural-wine");
    assertThat(persisted.label()).isEqualTo("Natural wine");
    assertThat(persisted.createdAt()).isEqualTo(NOW);
    assertThat(result).isEqualTo(persisted);
  }

  @Test
  void create_propagates_repository_failure() {
    when(tags.save(any(Tag.class))).thenThrow(new DataIntegrityViolationException("uq_tag_slug"));

    assertThatThrownBy(() -> service.create(TagCategory.FOOD, "natural-wine", "Natural wine"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uq_tag_slug");
  }

  @Test
  void list_delegates_to_repository() {
    Tag a = new Tag(TagId.newId(), TagCategory.FOOD, "natural-wine", "Natural wine", NOW);
    Tag b = new Tag(TagId.newId(), TagCategory.REGIME, "vegan", "Vegan", NOW);
    when(tags.findAll()).thenReturn(List.of(a, b));

    List<Tag> result = service.list();

    assertThat(result).containsExactly(a, b);
    verify(tags).findAll();
  }

  @Test
  void delete_persists_then_publishes_TagDeleted_event() {
    TagId id = TagId.newId();
    Tag existing = new Tag(id, TagCategory.FOOD, "natural-wine", "Natural wine", NOW);
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
}
