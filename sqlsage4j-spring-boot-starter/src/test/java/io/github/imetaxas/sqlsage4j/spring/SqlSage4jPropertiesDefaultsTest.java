package io.github.imetaxas.sqlsage4j.spring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SqlSage4jPropertiesDefaultsTest {

  @Test
  void defaultsAreReasonable() {
    SqlSage4jProperties props = new SqlSage4jProperties();

    assertThat(props.isEnabled()).isTrue();
    assertThat(props.getMaxTokens()).isEqualTo(4096L);
    assertThat(props.getTemperature()).isEqualTo(0.0);
    assertThat(props.getModelName()).isNull();
    assertThat(props.getApiKey()).isNull();
    assertThat(props.getBaseUrl()).isNull();
  }

  @Test
  void embeddingsDefaultsAreReasonable() {
    SqlSage4jProperties.Embeddings embeddings = new SqlSage4jProperties.Embeddings();

    assertThat(embeddings.getProvider()).isEqualTo("openai");
    assertThat(embeddings.getModel()).isEqualTo("text-embedding-3-small");
    assertThat(embeddings.getBaseUrl()).isNull();
    assertThat(embeddings.getApiKey()).isNull();
  }

  @Test
  void storageDefaultsAreReasonable() {
    SqlSage4jProperties.Storage storage = new SqlSage4jProperties.Storage();

    assertThat(storage.getType()).isEqualTo("in-memory");
  }

  @Test
  void safetyDefaultsAreReasonable() {
    SqlSage4jProperties.Safety safety = new SqlSage4jProperties.Safety();

    assertThat(safety.isReadOnly()).isFalse();
  }

  @Test
  void databaseDefaultsAreReasonable() {
    SqlSage4jProperties.Database database = new SqlSage4jProperties.Database();

    assertThat(database.getDialect()).isNull();
    assertThat(database.getDsn()).isNull();
  }
}
