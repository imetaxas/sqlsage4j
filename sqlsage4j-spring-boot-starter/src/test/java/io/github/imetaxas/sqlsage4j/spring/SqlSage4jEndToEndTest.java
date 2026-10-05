package io.github.imetaxas.sqlsage4j.spring;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;
import static io.github.imetaxas.realitycheck.RealityAssertions.assertThatThrownBy;

import io.github.imetaxas.sqlsage4j.ChatMessage;
import io.github.imetaxas.sqlsage4j.QueryChat;
import io.github.imetaxas.sqlsage4j.QueryResponse;
import io.github.imetaxas.sqlsage4j.SqlSage4j;
import io.github.imetaxas.sqlsage4j.client.LLMClient;
import io.github.imetaxas.sqlsage4j.pipeline.SqlGuard;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

/**
 * Full end-to-end test: Spring context boots → QueryChat is injected → DDLs are trained → user asks
 * a question → SQL is returned through the entire pipeline.
 */
@SpringBootTest
@TestPropertySource(
    properties = {
      "sqlsage4j.model-name=test-model",
      "sqlsage4j.base-url=http://localhost:11434",
      "sqlsage4j.max-tokens=1024",
      "sqlsage4j.temperature=0.0",
      "sqlsage4j.embeddings.provider=noop",
      "sqlsage4j.storage.type=bm25",
      "sqlsage4j.safety.read-only=true"
    })
class SqlSage4jEndToEndTest {

  @SpringBootApplication
  static class TestApp {

    @Bean
    @Primary
    public LLMClient testLlmClient() {
      return new FakeLLMClient();
    }
  }

  @Autowired private QueryChat queryChat;
  @Autowired private SqlSage4j sqlSage4j;
  @Autowired private SqlGuard sqlGuard;
  @Autowired private LLMClient llmClient;

  @Test
  void contextLoads() {
    assertThat(queryChat).isNotNull();
    assertThat(sqlSage4j).isNotNull();
    assertThat(sqlGuard).isNotNull();
  }

  @Test
  void fullPipeline_trainThenAsk_returnsSql() {
    queryChat.trainDdl("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, email TEXT);");
    queryChat.trainDdl(
        "CREATE TABLE orders (id INTEGER PRIMARY KEY, user_id INTEGER, total DECIMAL, status TEXT);");
    queryChat.train("How many users are there?", "SELECT COUNT(*) AS user_count FROM users;");

    QueryResponse response = queryChat.ask("Show me all users");

    assertThat(response).isNotNull();
    assertThat(response.sql()).isNotNull();
    assertThat(response.sql()).containsIgnoringCase("SELECT");
    assertThat(response.sql()).containsIgnoringCase("users");
    assertThat(response.isSuccess()).isTrue();
  }

  @Test
  void fullPipeline_askWithoutTraining_stillReturnsSql() {
    QueryResponse response = queryChat.ask("What tables exist?");

    assertThat(response).isNotNull();
    assertThat(response.sql()).isNotNull();
  }

  @Test
  void sqlGuard_blocksDestructiveSql() {
    assertThat(sqlGuard.check("DROP TABLE users;").blocked()).isTrue();
    assertThat(sqlGuard.check("DELETE FROM orders").blocked()).isTrue();
    assertThat(sqlGuard.check("SELECT * FROM users").blocked()).isFalse();
  }

  @Test
  void sqlGuard_integratedInQueryChat_blocksRunSql() {
    assertThatThrownBy(() -> queryChat.runSql("DROP TABLE users"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("blocked by safety guard");
  }

  @Test
  void trainAndRetrieve_roundTrip() {
    queryChat.trainDocumentation("Revenue is calculated as SUM of completed order totals.");

    String allData = queryChat.getAllTrainedData();
    assertThat(allData).contains("Revenue");
  }

  @Test
  void multiTurnConversation_worksEndToEnd() {
    queryChat.trainDdl("CREATE TABLE products (id INT, name TEXT, category TEXT, price DECIMAL);");

    QueryResponse first = queryChat.ask("Show products by category");
    assertThat(first.sql()).isNotNull();

    QueryResponse followUp = queryChat.ask("Only expensive ones");
    assertThat(followUp.sql()).isNotNull();
    assertThat(followUp.sql()).containsIgnoringCase("SELECT");
  }

  @Test
  void deleteTrainedData_worksEndToEnd() {
    String id = queryChat.trainDdl("CREATE TABLE temp (x INT);");
    assertThat(queryChat.getAllTrainedData()).contains("temp");

    boolean removed = queryChat.deleteTrainedData(id);
    assertThat(removed).isTrue();
  }

  /**
   * Fake LLM client that returns deterministic SQL responses based on the user message content.
   * Simulates what a real LLM would return for common text-to-SQL questions.
   */
  static final class FakeLLMClient implements LLMClient {
    private final List<List<ChatMessage>> history = new ArrayList<>();

    @Override
    public String submitPrompt(List<ChatMessage> messages) {
      history.add(List.copyOf(messages));
      String lastUserMsg = "";
      for (int i = messages.size() - 1; i >= 0; i--) {
        if (messages.get(i).role() == io.github.imetaxas.sqlsage4j.ChatRole.USER) {
          lastUserMsg = messages.get(i).content();
          break;
        }
      }

      String lower = lastUserMsg.toLowerCase();
      if (lower.contains("users")) {
        return "SELECT * FROM users;";
      } else if (lower.contains("products") && lower.contains("expensive")) {
        return "SELECT * FROM products WHERE price > 100;";
      } else if (lower.contains("products")) {
        return "SELECT category, COUNT(*) FROM products GROUP BY category;";
      } else if (lower.contains("orders")) {
        return "SELECT * FROM orders;";
      } else if (lower.contains("tables")) {
        return "SELECT name FROM sqlite_master WHERE type='table';";
      }
      return "SELECT 1;";
    }

    @Override
    public String modelName() {
      return "fake-e2e-model";
    }
  }
}
