package fr.lepgu.palaisdivin.backend.tag.adapters.rest;

import fr.lepgu.palaisdivin.backend.tag.domain.ports.ListTagsUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/tags")
class PublicTagRestController {

  private final ListTagsUseCase listTags;

  PublicTagRestController(ListTagsUseCase listTags) {
    this.listTags = listTags;
  }

  @GetMapping
  TagCatalogResponse list() {
    return TagCatalogResponse.from(listTags.list());
  }
}
