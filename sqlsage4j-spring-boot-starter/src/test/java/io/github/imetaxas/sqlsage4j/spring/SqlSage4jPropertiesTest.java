package io.github.imetaxas.sqlsage4j.spring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = SqlSage4jPropertiesTest.Config.class)
@TestPropertySource(
    properties = {
      "sqlsage4j.model-name=gpt-4o",
      "sqlsage4j.api-key=sk-test-123",
      "sqlsage4j.base-url=http://localhost:11434",
      "sqlsage4j.max-tokens=2048",
      "sqlsage4j.temperature=0.1",
      "sqlsage4j.embeddings.provider=openai",
      "sqlsage4j.embeddings.model=text-embedding-3-small",
      "sqlsage4j.storage.type=in-memory",
      "sqlsage4j.database.dialect=postgresql",
      "sqlsage4j.safety.read-only=true",
      "sqlsage4j.enabled=true"
    })
class SqlSage4jPropertiesTest {

  @EnableConfigurationProperties(SqlSage4jProperties.class)
  static class Config {}

  @Autowired private SqlSage4jProperties properties;

  @Test
  void bindsModelName() {
    assertThat(properties.getModelName()).isEqualTo("gpt-4o");
  }

  @Test
  void bindsApiKey() {
    assertThat(properties.getApiKey()).isEqualTo("sk-test-123");
  }

  @Test
  void bindsBaseUrl() {
    assertThat(properties.getBaseUrl()).isEqualTo("http://localhost:11434");
  }

  @Test
  void bindsMaxTokens() {
    assertThat(properties.getMaxTokens()).isEqualTo(2048L);
  }

  @Test
  void bindsTemperature() {
    assertThat(properties.getTemperature()).isEqualTo(0.1);
  }

  @Test
  void bindsEmbeddingsProvider() {
    assertThat(properties.getEmbeddings().getProvider()).isEqualTo("openai");
  }

  @Test
  void bindsEmbeddingsModel() {
    assertThat(properties.getEmbeddings().getModel()).isEqualTo("text-embedding-3-small");
  }

  @Test
  void bindsStorageType() {
    assertThat(properties.getStorage().getType()).isEqualTo("in-memory");
  }

  @Test
  void bindsDatabaseDialect() {
    assertThat(properties.getDatabase().getDialect()).isEqualTo("postgresql");
  }

  @Test
  void bindsSafetyReadOnly() {
    assertThat(properties.getSafety().isReadOnly()).isTrue();
  }

  @Test
  void bindsEnabled() {
    assertThat(properties.isEnabled()).isTrue();
  }
}
