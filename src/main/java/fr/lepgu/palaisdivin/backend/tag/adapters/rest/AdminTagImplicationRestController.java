package fr.lepgu.palaisdivin.backend.tag.adapters.rest;

import fr.lepgu.palaisdivin.backend.tag.domain.model.TagId;
import fr.lepgu.palaisdivin.backend.tag.domain.model.TagImplication;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.CreateTagImplicationUseCase;
import fr.lepgu.palaisdivin.backend.tag.domain.ports.DeleteTagImplicationUseCase;
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
@RequestMapping("/api/v1/admin/tag-implications")
class AdminTagImplicationRestController {

  private final CreateTagImplicationUseCase createImplication;
  private final DeleteTagImplicationUseCase deleteImplication;

  AdminTagImplicationRestController(
      CreateTagImplicationUseCase createImplication,
      DeleteTagImplicationUseCase deleteImplication) {
    this.createImplication = createImplication;
    this.deleteImplication = deleteImplication;
  }

  @PostMapping
  ResponseEntity<TagImplicationResponse> create(
      @Valid @RequestBody CreateTagImplicationRequest req) {
    TagImplication created =
        createImplication.create(new TagId(req.tagId()), new TagId(req.impliesTagId()));
    URI location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{tagId}/{impliesTagId}")
            .buildAndExpand(created.tagId().value(), created.impliesTagId().value())
            .toUri();
    return ResponseEntity.created(location).body(TagImplicationResponse.from(created));
  }

  @DeleteMapping("/{tagId}/{impliesTagId}")
  ResponseEntity<Void> delete(@PathVariable UUID tagId, @PathVariable UUID impliesTagId) {
    deleteImplication.delete(new TagId(tagId), new TagId(impliesTagId));
    return ResponseEntity.noContent().build();
  }
}
