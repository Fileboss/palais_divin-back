package fr.lepgu.palaisdivin.backend.shared.domain.valueobject;

import java.util.List;
import java.util.Objects;

public record Page<T>(List<T> data, boolean hasNext) {
  public Page {
    Objects.requireNonNull(data, "data");
    data = List.copyOf(data);
  }
}
