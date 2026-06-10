package fr.lepgu.palaisdivin.backend.tag.adapters.rest;

import fr.lepgu.palaisdivin.backend.tag.domain.model.TagCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateTagRequest(
    @NotNull TagCategory category,
    @NotBlank @Size(max = 64) @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$") String slug,
    @NotBlank @Size(max = 127) String label,
    @Size(max = 20)
        Map<
                @NotNull @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$") @Size(max = 8) String,
                @NotBlank @Size(max = 127) String>
            labelI18n) {
  public CreateTagRequest {
    if (labelI18n == null) {
      labelI18n = Map.of();
    }
  }
}
