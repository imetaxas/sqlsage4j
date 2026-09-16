package io.github.imetaxas.sqlsage4j.prompt;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;
import static io.github.imetaxas.realitycheck.RealityAssertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

final class PromptTemplateTest {

  @Test
  void loadsFromResource() {
    PromptTemplate template = PromptTemplate.fromResource("prompts/sql_generation_system.txt");
    assertThat(template.raw()).contains("expert");
    assertThat(template.raw()).contains("{dialect}");
  }

  @Test
  void rendersPlaceholders() {
    PromptTemplate template = PromptTemplate.fromResource("prompts/sql_generation_system.txt");
    String rendered = template.render(Map.of("dialect", "BigQuery SQL"));
    assertThat(rendered).contains("BigQuery SQL");
    assertThat(rendered).doesNotContain("{dialect}");
  }

  @Test
  void staticTemplateRenders() {
    PromptTemplate template = PromptTemplate.of("Hello {name}, welcome to {place}!");
    String rendered = template.render(Map.of("name", "Alice", "place", "Wonderland"));
    assertThat(rendered).isEqualTo("Hello Alice, welcome to Wonderland!");
  }

  @Test
  void missingResourceThrows() {
    assertThatThrownBy(() -> PromptTemplate.fromResource("prompts/nonexistent.txt"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
