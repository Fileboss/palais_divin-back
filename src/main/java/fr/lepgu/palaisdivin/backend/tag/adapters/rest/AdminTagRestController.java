package fr.lepgu.palaisdivin.backend.tag.adapters.rest;

import fr.lepgu.palaisdivin.backend.tag.domain.model.Tag;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.CreateTagUseCase;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/admin/tags")
class AdminTagRestController {

  private final CreateTagUseCase createTag;

  AdminTagRestController(CreateTagUseCase createTag) {
    this.createTag = createTag;
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
}
