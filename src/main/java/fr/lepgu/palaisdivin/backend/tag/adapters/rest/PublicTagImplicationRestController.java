package fr.lepgu.palaisdivin.backend.tag.adapters.rest;

import fr.lepgu.palaisdivin.backend.tag.domain.ports.ListTagImplicationsUseCase;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/tag-implications")
class PublicTagImplicationRestController {

  private final ListTagImplicationsUseCase listImplications;

  PublicTagImplicationRestController(ListTagImplicationsUseCase listImplications) {
    this.listImplications = listImplications;
  }

  @GetMapping
  TagImplicationsResponse list() {
    List<TagImplicationResponse> data =
        listImplications.listAll().stream().map(TagImplicationResponse::from).toList();
    return new TagImplicationsResponse(data);
  }

  record TagImplicationsResponse(List<TagImplicationResponse> data) {}
}
