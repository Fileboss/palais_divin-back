package fr.lepgu.palaisdivin.backend.shared.domain.valueobject;

import java.util.List;
import java.util.Objects;

public record CursorPage<T>(List<T> data, boolean hasNext) {
  public CursorPage {
    Objects.requireNonNull(data, "data");
    data = List.copyOf(data);
  }
}
