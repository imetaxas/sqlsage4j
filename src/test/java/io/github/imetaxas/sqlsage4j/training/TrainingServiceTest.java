package io.github.imetaxas.sqlsage4j.training;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.DDL;
import io.github.imetaxas.sqlsage4j.Ontology;
import io.github.imetaxas.sqlsage4j.QuestionAnswer;
import io.github.imetaxas.sqlsage4j.SampleDocument;
import io.github.imetaxas.sqlsage4j.client.MockLLMClient;
import io.github.imetaxas.sqlsage4j.provider.MockEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.InMemoryEmbeddingsStorage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class TrainingServiceTest {

  private TrainingService service;
  private MockLLMClient mockClient;

  @BeforeEach
  void setUp() {
    mockClient = new MockLLMClient();
    service =
        new TrainingService(
            new MockEmbeddingsProvider(), new InMemoryEmbeddingsStorage(), mockClient);
  }

  @Test
  void trainDdl() {
    service.trainDdl("CREATE TABLE users (id INT, name TEXT);");
    List<String> related = service.getRelatedDdl("users");
    assertThat(related).hasSize(1);
    assertThat(related.get(0)).contains("CREATE TABLE users");
  }

  @Test
  void trainQuestionSql() {
    service.trainQuestionSql("How many users?", "SELECT count(*) FROM users;");
    List<QuestionAnswer> similar = service.getSimilarQuestionSql("How many users?");
    assertThat(similar).hasSize(1);
    assertThat(similar.get(0).question()).isEqualTo("How many users?");
    assertThat(similar.get(0).sql()).isEqualTo("SELECT count(*) FROM users;");
  }

  @Test
  void trainDocumentation() {
    service.trainDocumentation("Revenue is the total of all sales.");
    List<String> docs = service.getRelatedDocumentation("revenue");
    assertThat(docs).hasSize(1);
    assertThat(docs.get(0)).contains("Revenue");
  }

  @Test
  void trainFromDocument() {
    service.train(new DDL("CREATE TABLE orders (id INT);"));
    service.train(new QuestionAnswer("What is the latest order?", "SELECT * FROM orders LIMIT 1;"));
    service.train(new SampleDocument("Orders contain all customer purchases."));
    service.train(new Ontology("Business ontology content"));

    assertThat(service.getAllTrainingData()).hasSize(4);
  }

  @Test
  void trainSqlAutoQuestion() {
    mockClient.withResponse("What are the top selling products?");
    service.trainSqlAutoQuestion(
        "SELECT product, SUM(qty) FROM sales GROUP BY product ORDER BY 2 DESC;");

    assertThat(mockClient.callCount()).isEqualTo(1);
    List<QuestionAnswer> similar = service.getSimilarQuestionSql("top products");
    assertThat(similar).isNotEmpty();
  }

  @Test
  void removeTrainingData() {
    String id = service.trainDdl("CREATE TABLE test (id INT);");
    assertThat(service.getAllTrainingData()).hasSize(1);
    assertThat(service.removeTrainingData(id)).isTrue();
    assertThat(service.getAllTrainingData()).isEmpty();
  }

  @Test
  void removeAllTrainingData() {
    service.trainDdl("CREATE TABLE a (id INT);");
    service.trainDdl("CREATE TABLE b (id INT);");
    service.trainDocumentation("some doc");
    assertThat(service.getAllTrainingData()).hasSize(3);

    service.removeAllTrainingData();
    assertThat(service.getAllTrainingData()).isEmpty();
  }
}
