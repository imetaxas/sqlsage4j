package io.github.imetaxas.sqlsage4j.prompt;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.ChatMessage;
import io.github.imetaxas.sqlsage4j.ChatRole;
import io.github.imetaxas.sqlsage4j.QuestionAnswer;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SqlPromptBuilderTest {

  @Test
  void buildsMinimalPrompt() {
    List<ChatMessage> messages =
        SqlPromptBuilder.build(
            null, "How many users?", List.of(), List.of(), List.of(), "BigQuery SQL", 14000);

    assertThat(messages.size()).isGreaterThanOrEqualTo(2);
    assertThat(messages.get(0).role()).as("first message role").isEqualTo(ChatRole.SYSTEM);
    assertThat(messages.get(0).content()).contains("BigQuery SQL");
    assertThat(messages.get(0).content()).contains("Response Guidelines");
    assertThat(messages.get(messages.size() - 1).role())
        .as("last message role")
        .isEqualTo(ChatRole.USER);
    assertThat(messages.get(messages.size() - 1).content()).isEqualTo("How many users?");
  }

  @Test
  void includesDdlsInSystemPrompt() {
    List<ChatMessage> messages =
        SqlPromptBuilder.build(
            null,
            "How many users?",
            List.of(),
            List.of("CREATE TABLE users (id INT, name TEXT);"),
            List.of(),
            "SQL",
            14000);

    String systemContent = messages.get(0).content();
    assertThat(systemContent).contains("===Tables");
    assertThat(systemContent).contains("CREATE TABLE users");
  }

  @Test
  void includesDocumentationInSystemPrompt() {
    List<ChatMessage> messages =
        SqlPromptBuilder.build(
            null,
            "What is revenue?",
            List.of(),
            List.of(),
            List.of("Revenue is calculated as sum of all transactions."),
            "SQL",
            14000);

    String systemContent = messages.get(0).content();
    assertThat(systemContent).contains("===Additional Context");
    assertThat(systemContent).contains("Revenue is calculated");
  }

  @Test
  void includesFewShotExamples() {
    List<QuestionAnswer> qaPairs =
        List.of(
            new QuestionAnswer("How many orders?", "SELECT count(*) FROM orders;"),
            new QuestionAnswer("Top customers?", "SELECT * FROM customers ORDER BY sales DESC;"));

    List<ChatMessage> messages =
        SqlPromptBuilder.build(null, "New question?", qaPairs, List.of(), List.of(), "SQL", 14000);

    // System + 2 user/assistant pairs + final user = 6 messages
    assertThat(messages).hasSize(6);
    assertThat(messages.get(1).role()).as("first example role").isEqualTo(ChatRole.USER);
    assertThat(messages.get(1).content()).isEqualTo("How many orders?");
    assertThat(messages.get(2).role()).as("first answer role").isEqualTo(ChatRole.ASSISTANT);
    assertThat(messages.get(2).content()).isEqualTo("SELECT count(*) FROM orders;");
  }

  @Test
  void respectsCustomInitialPrompt() {
    List<ChatMessage> messages =
        SqlPromptBuilder.build(
            "You are a professional data expert.",
            "question",
            List.of(),
            List.of(),
            List.of(),
            "SQL",
            14000);

    assertThat(messages.get(0).content()).startsWith("You are a professional data expert.");
  }
}
