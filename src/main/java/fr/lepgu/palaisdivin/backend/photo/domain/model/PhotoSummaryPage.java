package fr.lepgu.palaisdivin.backend.photo.domain.model;

import java.util.List;
import java.util.Objects;

public record PhotoSummaryPage(List<PhotoSummary> data, boolean hasNext, PhotoCursor nextCursor) {
  public PhotoSummaryPage {
    Objects.requireNonNull(data, "data");
    data = List.copyOf(data);
  }
}
