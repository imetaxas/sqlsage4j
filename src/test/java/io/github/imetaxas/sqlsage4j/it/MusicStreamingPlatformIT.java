package io.github.imetaxas.sqlsage4j.it;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.*;
import io.github.imetaxas.sqlsage4j.client.MockLLMClient;
import io.github.imetaxas.sqlsage4j.db.DataFrame;
import io.github.imetaxas.sqlsage4j.db.MockDatabaseConnector;
import io.github.imetaxas.sqlsage4j.enums.PromptEnum;
import io.github.imetaxas.sqlsage4j.enums.StorageEnum;
import io.github.imetaxas.sqlsage4j.provider.MockEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.storage.H2EmbeddingsStorage;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Scenario 2: Music Streaming Platform — uses H2EmbeddingsStorage (H2 in-memory SQL database).
 *
 * <p>A product manager queries streaming data — top artists, playlist engagement, user listening
 * trends. Uses multi-turn conversations to drill down from broad overviews to specific segments.
 */
final class MusicStreamingPlatformIT {

  private MockLLMClient llmClient;
  private MockDatabaseConnector db;
  private H2EmbeddingsStorage h2;
  private QueryChat queryChat;

  @BeforeEach
  void setUp() {
    llmClient = new MockLLMClient();
    db =
        new MockDatabaseConnector("BigQuery SQL")
            .withDefaultResult(new DataFrame(List.of("result"), List.of(List.of("value"))));
    h2 = new H2EmbeddingsStorage();

    queryChat =
        SqlSage4j.builder(
                LLMProviderConfig.builder("mock-model")
                    .llmClient(llmClient)
                    .embeddingsProvider(new MockEmbeddingsProvider())
                    .embeddingsStorage(h2)
                    .maxTokens(14000L)
                    .build())
            .connectToStorage(StorageEnum.BIGQUERY)
            .databaseConnector(db)
            .prompt(
                Prompt.builder()
                    .userPrompt(PromptEnum.DATA_SCIENTIST)
                    .ddls(
                        List.of(
                            new DDL(
                                """
                    CREATE TABLE streams (
                      stream_id INT PRIMARY KEY,
                      user_id INT,
                      track_id INT,
                      artist_id INT,
                      streamed_at TIMESTAMP,
                      duration_ms INT,
                      platform STRING,
                      country STRING
                    );"""),
                            new DDL(
                                """
                    CREATE TABLE artists (
                      artist_id INT PRIMARY KEY,
                      name STRING,
                      genre STRING,
                      monthly_listeners INT,
                      verified BOOLEAN
                    );"""),
                            new DDL(
                                """
                    CREATE TABLE playlists (
                      playlist_id INT PRIMARY KEY,
                      name STRING,
                      owner_id INT,
                      follower_count INT,
                      is_editorial BOOLEAN,
                      created_at DATE
                    );"""),
                            new DDL(
                                """
                    CREATE TABLE playlist_tracks (
                      playlist_id INT,
                      track_id INT,
                      position INT,
                      added_at DATE
                    );""")))
                    .sampleQuestionsAnswers(
                        List.of(
                            new QuestionAnswer(
                                "Top 10 artists by streams this month",
                                "SELECT a.name, COUNT(*) AS stream_count FROM streams s JOIN artists a ON s.artist_id = a.artist_id WHERE s.streamed_at >= DATE_TRUNC(CURRENT_DATE(), MONTH) GROUP BY a.name ORDER BY stream_count DESC LIMIT 10;")))
                    .sampleDocuments(
                        List.of(
                            new SampleDocument(
                                "A stream is counted when duration_ms >= 30000 (30 seconds). Shorter plays are skipped events and should be excluded from stream counts."),
                            new SampleDocument(
                                "Editorial playlists (is_editorial = TRUE) are curated by the platform. User playlists are community-created.")))
                    .build())
            .build()
            .queryChat();
  }

  @AfterEach
  void tearDown() {
    h2.close();
  }

  @Test
  void topArtistsByStreams() {
    llmClient.withResponse(
        "SELECT a.name, COUNT(*) AS stream_count "
            + "FROM streams s JOIN artists a ON s.artist_id = a.artist_id "
            + "WHERE s.streamed_at >= DATE_TRUNC(CURRENT_DATE(), MONTH) AND s.duration_ms >= 30000 "
            + "GROUP BY a.name ORDER BY stream_count DESC LIMIT 10;");

    QueryResponse response = queryChat.ask("Who are the top 10 most streamed artists this month?");

    assertThat(response.isSuccess()).isTrue();
    assertThat(response.sql()).containsIgnoringCase("artists");
    assertThat(response.sql()).containsIgnoringCase("ORDER BY");
    assertThat(response.sql()).containsIgnoringCase("LIMIT 10");
  }

  @Test
  void documentationContext_30SecondRule() {
    llmClient.withResponse("SELECT COUNT(*) FROM streams WHERE duration_ms >= 30000;");
    queryChat.ask("How many total streams happened last week?");

    String systemPrompt = llmClient.lastPrompt().get(0).content();
    assertThat(systemPrompt).contains("30000");
    assertThat(systemPrompt).contains("duration_ms >= 30000");
  }

  @Test
  void multiTurnDrillDown() {
    llmClient.withResponse(
        "SELECT genre, COUNT(*) AS streams FROM streams s JOIN artists a ON s.artist_id = a.artist_id GROUP BY genre ORDER BY streams DESC;");
    QueryResponse r1 = queryChat.ask("What are the most popular genres?");
    assertThat(r1.isSuccess()).isTrue();

    llmClient
        .withResponse("What are the most popular genres in the US?")
        .withResponse(
            "SELECT genre, COUNT(*) FROM streams s JOIN artists a ON s.artist_id = a.artist_id WHERE s.country = 'US' GROUP BY genre ORDER BY 2 DESC;");

    QueryResponse r2 = queryChat.ask("What about just in the US?");
    assertThat(r2.isSuccess()).isTrue();
    assertThat(r2.sql()).containsIgnoringCase("US");

    assertThat(llmClient.callCount())
        .isEqualTo(3); // Turn1:generate + Turn2:rewrite + Turn2:generate
  }

  @Test
  void editorialVsUserPlaylists_contextFromTraining() {
    llmClient.withResponse(
        "SELECT CASE WHEN is_editorial THEN 'Editorial' ELSE 'User' END AS type, AVG(follower_count) FROM playlists GROUP BY type;");

    queryChat.ask("Compare average followers between editorial and user playlists");

    String systemPrompt = llmClient.lastPrompt().get(0).content();
    assertThat(systemPrompt).contains("is_editorial");
  }
}
