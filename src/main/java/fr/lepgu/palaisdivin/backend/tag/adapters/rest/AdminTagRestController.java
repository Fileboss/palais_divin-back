package fr.lepgu.palaisdivin.backend.tag.adapters.rest;

import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.CreateTagUseCase;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.DeleteTagUseCase;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/admin/tags")
class AdminTagRestController {

  private final CreateTagUseCase createTag;
  private final DeleteTagUseCase deleteTag;

  AdminTagRestController(CreateTagUseCase createTag, DeleteTagUseCase deleteTag) {
    this.createTag = createTag;
    this.deleteTag = deleteTag;
  }

  @PostMapping
  ResponseEntity<TagResponse> create(@Valid @RequestBody CreateTagRequest req) {
    Tag created = createTag.create(req.category(), req.slug(), req.label());
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id().value())
            .toUri();
    return ResponseEntity.created(location).body(TagResponse.from(created));
  }

  @DeleteMapping("/{tagId}")
  ResponseEntity<Void> delete(@PathVariable UUID tagId) {
    deleteTag.delete(new TagId(tagId));
    return ResponseEntity.noContent().build();
  }
}
