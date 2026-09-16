package io.github.imetaxas.sqlsage4j.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class PromptTemplate {

  private final String template;

  private PromptTemplate(String template) {
    this.template = template;
  }

  public static PromptTemplate fromResource(String resourcePath) {
    try (InputStream is = PromptTemplate.class.getClassLoader().getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new IllegalArgumentException("Prompt resource not found: " + resourcePath);
      }
      return new PromptTemplate(new String(is.readAllBytes(), StandardCharsets.UTF_8).trim());
    } catch (IOException e) {
      throw new RuntimeException("Failed to load prompt template: " + resourcePath, e);
    }
  }

  public static PromptTemplate of(String template) {
    return new PromptTemplate(template);
  }

  public String render(Map<String, String> vars) {
    String result = template;
    for (Map.Entry<String, String> entry : vars.entrySet()) {
      result = result.replace("{" + entry.getKey() + "}", entry.getValue());
    }
    return result;
  }

  public String render() {
    return template;
  }

  public String raw() {
    return template;
  }
}
