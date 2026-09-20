package io.github.imetaxas.sqlsage4j;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.enums.PromptEnum;
import io.github.imetaxas.sqlsage4j.enums.RerankingAlgorithmEnum;
import io.github.imetaxas.sqlsage4j.provider.MockEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.InMemoryEmbeddingsStorage;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SqlSage4jTest {

  @Test
  void buildMinimalConfig() {
    SqlSage4j sqlSage4j =
        SqlSage4j.builder(
                LLMProviderConfig.builder("gpt-4o")
                    .apiKey("test-key")
                    .maxTokens(1000L)
                    .temperature(0.7)
                    .embeddingsProvider(new MockEmbeddingsProvider())
                    .embeddingsStorage(new InMemoryEmbeddingsStorage())
                    .build())
            .prompt(Prompt.builder().userPrompt(PromptEnum.DATA_SCIENTIST).build())
            .build();

    assertThat(sqlSage4j).as("sqlSage4j instance").isNotNull();
    assertThat(sqlSage4j.llmProviderConfig().modelName()).isEqualTo("gpt-4o");
    assertThat(sqlSage4j.prompt().userPrompt())
        .as("user prompt")
        .isEqualTo(PromptEnum.DATA_SCIENTIST);
  }

  @Test
  void buildWithTrainingData() {
    SqlSage4j sqlSage4j =
        SqlSage4j.builder(
                LLMProviderConfig.builder("gpt-4o")
                    .apiKey("test-key")
                    .maxTokens(1000L)
                    .temperature(0.7)
                    .embeddingsProvider(new MockEmbeddingsProvider())
                    .embeddingsStorage(new InMemoryEmbeddingsStorage())
                    .build())
            .train(
                List.of(
                    new DDL("CREATE TABLE users (id INT);"),
                    new QuestionAnswer("count users", "SELECT count(*) FROM users;")))
            .prompt(Prompt.builder().userPrompt(PromptEnum.DATA_SCIENTIST).build())
            .rerankingAlgorithm(RerankingAlgorithmEnum.GPT_RANKING)
            .build();

    assertThat(sqlSage4j.train()).hasSize(2);
    assertThat(sqlSage4j.rerankingAlgorithm())
        .as("reranking algorithm")
        .isEqualTo(RerankingAlgorithmEnum.GPT_RANKING);
  }

  @Test
  void buildWithPromptContext() {
    SqlSage4j sqlSage4j =
        SqlSage4j.builder(
                LLMProviderConfig.builder("gpt-4o")
                    .apiKey("test-key")
                    .maxTokens(2048L)
                    .embeddingsProvider(new MockEmbeddingsProvider())
                    .embeddingsStorage(new InMemoryEmbeddingsStorage())
                    .build())
            .prompt(
                Prompt.builder()
                    .userPrompt(PromptEnum.SQL_EXPERT)
                    .initialPrompt("You are a professional data expert.")
                    .ddls(List.of(new DDL("CREATE TABLE streams (id INT);")))
                    .sampleQuestionsAnswers(
                        List.of(
                            new QuestionAnswer("top streams?", "SELECT * FROM streams LIMIT 10;")))
                    .sampleDocuments(List.of(new SampleDocument("Streams table docs.")))
                    .ontologies(List.of(new Ontology("domain ontology")))
                    .queries(List.of("SELECT 1;"))
                    .build())
            .build();

    assertThat(sqlSage4j.prompt().initialPrompt()).isEqualTo("You are a professional data expert.");
    assertThat(sqlSage4j.prompt().ddls()).hasSize(1);
    assertThat(sqlSage4j.prompt().sampleQuestionsAnswers()).hasSize(1);
  }

  @Test
  void documentInterface() {
    Document ddl = new DDL("CREATE TABLE t(id INT);");
    Document qa = new QuestionAnswer("q", "SELECT 1;");
    Document doc = new SampleDocument("content", "/path");
    Document ont = new Ontology("ontology");

    assertThat(ddl.content()).contains("CREATE TABLE");
    assertThat(qa.content()).contains("SELECT");
    assertThat(doc.content()).isEqualTo("content");
    assertThat(ont.content()).isEqualTo("ontology");
  }
}
