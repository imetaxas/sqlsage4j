package io.github.imetaxas.sqlsage4j.training;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.client.MockLLMClient;
import io.github.imetaxas.sqlsage4j.provider.MockEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.InMemoryEmbeddingsStorage;
import io.github.imetaxas.sqlsage4j.storage.TrainingRecord;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class TrainingDataIOTest {

  private TrainingService trainingService;

  @BeforeEach
  void setUp() {
    trainingService =
        new TrainingService(
            new MockEmbeddingsProvider(), new InMemoryEmbeddingsStorage(), new MockLLMClient());
  }

  @Test
  void exportAndImport_roundTrip() {
    trainingService.trainDdl("CREATE TABLE users (id INT, name TEXT);");
    trainingService.trainQuestionSql("How many users?", "SELECT count(*) FROM users;");
    trainingService.trainDocumentation("Users table stores registered users.");

    String json = TrainingDataIO.exportToString(trainingService);

    assertThat(json).contains("CREATE TABLE users");
    assertThat(json).contains("How many users?");
    assertThat(json).contains("Users table stores");
    assertThat(json).contains("\"version\"");
    assertThat(json).contains("\"recordCount\"");

    TrainingService newService =
        new TrainingService(
            new MockEmbeddingsProvider(), new InMemoryEmbeddingsStorage(), new MockLLMClient());

    int imported = TrainingDataIO.importFromString(newService, json);
    assertThat(imported).isEqualTo(3);

    List<TrainingRecord> allData = newService.getAllTrainingData();
    assertThat(allData).hasSize(3);
  }

  @Test
  void exportToString_emptyService_producesValidJson() {
    String json = TrainingDataIO.exportToString(trainingService);
    assertThat(json).contains("\"recordCount\": 0");
    assertThat(json).contains("\"records\": []");
  }

  @Test
  void importFromString_preservesMetadata() {
    trainingService.trainQuestionSql("Revenue?", "SELECT sum(total) FROM orders;");

    String json = TrainingDataIO.exportToString(trainingService);
    assertThat(json).contains("\"question\"");
    assertThat(json).contains("\"sql\"");

    TrainingService newService =
        new TrainingService(
            new MockEmbeddingsProvider(), new InMemoryEmbeddingsStorage(), new MockLLMClient());
    TrainingDataIO.importFromString(newService, json);

    List<TrainingRecord> data = newService.getAllTrainingData();
    assertThat(data).hasSize(1);
    assertThat(data.get(0).content()).contains("Revenue?");
  }

  @Test
  void importFromString_handlesQaWithoutMetadata() {
    String json =
        """
        {
          "version": 1,
          "recordCount": 1,
          "records": [
            {"id": "1", "type": "SQL_QA", "content": "How many?\\nSELECT count(*) FROM t;"}
          ]
        }
        """;

    int imported = TrainingDataIO.importFromString(trainingService, json);
    assertThat(imported).isEqualTo(1);
    assertThat(trainingService.getAllTrainingData()).hasSize(1);
  }
}
