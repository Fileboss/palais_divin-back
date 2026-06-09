package fr.lepgu.palaisdivin.backend.shared.adapters.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TagGroupParserTest {

  @Test
  void null_or_empty_returns_empty_list() {
    assertThat(TagGroupParser.parse(null)).isEmpty();
    assertThat(TagGroupParser.parse(List.of())).isEmpty();
  }

  @Test
  void singleton_value_becomes_one_group_with_one_slug() {
    List<List<String>> groups = TagGroupParser.parse(List.of("vegan"));

    assertThat(groups).containsExactly(List.of("vegan"));
  }

  @Test
  void comma_splits_into_or_group() {
    List<List<String>> groups = TagGroupParser.parse(List.of("vegan,vegan-friendly"));

    assertThat(groups).containsExactly(List.of("vegan", "vegan-friendly"));
  }

  @Test
  void repeated_param_makes_and_across_or_groups() {
    List<List<String>> groups = TagGroupParser.parse(List.of("vegan,vegan-friendly", "terrace"));

    assertThat(groups).containsExactly(List.of("vegan", "vegan-friendly"), List.of("terrace"));
  }

  @Test
  void total_over_ten_throws() {
    assertThatThrownBy(() -> TagGroupParser.parse(List.of("a,b,c,d,e,f,g,h,i,j", "k")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("total");
  }

  @Test
  void per_group_over_ten_throws() {
    assertThatThrownBy(() -> TagGroupParser.parse(List.of("a,b,c,d,e,f,g,h,i,j,k")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void whitespace_around_commas_is_trimmed() {
    List<List<String>> groups = TagGroupParser.parse(List.of("vegan , vegan-friendly"));

    assertThat(groups).containsExactly(List.of("vegan", "vegan-friendly"));
  }
}
