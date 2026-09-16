package io.github.imetaxas.sqlsage4j.storage;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.provider.NoOpEmbeddingsProvider;
import io.github.imetaxas.sqlsage4j.training.TrainingDataType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class BM25StorageTest {

  private BM25Storage storage;
  private NoOpEmbeddingsProvider embeddings;

  @BeforeEach
  void setUp() {
    storage = new BM25Storage();
    embeddings = new NoOpEmbeddingsProvider();
  }

  private TrainingRecord record(String content) {
    return new TrainingRecord(
        UUID.randomUUID().toString(),
        TrainingDataType.SQL_QA,
        content,
        embeddings.generateEmbedding(content),
        Map.of());
  }

  @Test
  void store_returnsId() {
    TrainingRecord rec = record("CREATE TABLE users (id INT, name TEXT)");
    String id = storage.store("ddl", rec);
    assertThat(id).isNotNull();
    assertThat(id).isNotBlank();
  }

  @Test
  void search_returnsRelevantResults() {
    storage.store("qa", record("How many active users are there?"));
    storage.store("qa", record("Total revenue last month"));
    storage.store("qa", record("Top products by sales"));

    float[] query = embeddings.generateEmbedding("users count active");
    List<SearchResult> results = storage.search("qa", query, 3);

    assertThat(results).isNotEmpty();
    assertThat(results.get(0).record().content()).contains("active users");
  }

  @Test
  void search_ranksExactMatchHigher() {
    storage.store("qa", record("revenue by product category"));
    storage.store("qa", record("total revenue last quarter"));
    storage.store("qa", record("user signups by country"));

    float[] query = embeddings.generateEmbedding("revenue by product");
    List<SearchResult> results = storage.search("qa", query, 3);

    assertThat(results.get(0).record().content()).contains("revenue by product category");
  }

  @Test
  void search_emptyCollection_returnsEmpty() {
    float[] query = embeddings.generateEmbedding("anything");
    List<SearchResult> results = storage.search("nonexistent", query, 5);
    assertThat(results).isEmpty();
  }

  @Test
  void search_limitsResults() {
    for (int i = 0; i < 10; i++) {
      storage.store("docs", record("document number " + i + " about data"));
    }

    float[] query = embeddings.generateEmbedding("data document");
    List<SearchResult> results = storage.search("docs", query, 3);

    assertThat(results).hasSize(3);
  }

  @Test
  void search_nullEmbedding_returnsFirstN() {
    storage.store("qa", record("first item"));
    storage.store("qa", record("second item"));

    List<SearchResult> results = storage.search("qa", new float[] {0f}, 2);
    assertThat(results).hasSize(2);
  }

  @Test
  void getAll_returnsAllStoredRecords() {
    storage.store("ddl", record("CREATE TABLE a"));
    storage.store("ddl", record("CREATE TABLE b"));
    storage.store("other", record("some doc"));

    List<TrainingRecord> all = storage.getAll("ddl");
    assertThat(all).hasSize(2);
  }

  @Test
  void getAll_emptyCollection_returnsEmpty() {
    List<TrainingRecord> all = storage.getAll("nothing");
    assertThat(all).isEmpty();
  }

  @Test
  void remove_deletesRecord() {
    TrainingRecord rec = record("test content");
    String id = storage.store("qa", rec);

    boolean removed = storage.remove(id);
    assertThat(removed).isTrue();
    assertThat(storage.getAll("qa")).isEmpty();
  }

  @Test
  void remove_nonexistentId_returnsFalse() {
    boolean removed = storage.remove("nonexistent-id");
    assertThat(removed).isFalse();
  }

  @Test
  void removeAll_clearsCollection() {
    storage.store("qa", record("item 1"));
    storage.store("qa", record("item 2"));

    storage.removeAll("qa");
    assertThat(storage.getAll("qa")).isEmpty();
  }

  @Test
  void tokenize_handlesSpecialCharacters() {
    List<String> tokens = BM25Storage.tokenize("SELECT COUNT(*) FROM users WHERE id > 5");
    assertThat(tokens).contains("select");
    assertThat(tokens).contains("count");
    assertThat(tokens).contains("users");
  }

  @Test
  void tokenize_removesStopWords() {
    List<String> tokens = BM25Storage.tokenize("what is the total count of all users");
    assertThat(tokens).doesNotContain("what");
    assertThat(tokens).doesNotContain("is");
    assertThat(tokens).doesNotContain("the");
    assertThat(tokens).doesNotContain("of");
    assertThat(tokens).contains("total");
    assertThat(tokens).contains("count");
    assertThat(tokens).contains("users");
  }

  @Test
  void tokenize_nullOrBlank_returnsEmpty() {
    assertThat(BM25Storage.tokenize(null)).isEmpty();
    assertThat(BM25Storage.tokenize("")).isEmpty();
    assertThat(BM25Storage.tokenize("   ")).isEmpty();
  }

  @Test
  void search_scoresSortedDescending() {
    storage.store("qa", record("SELECT name FROM users"));
    storage.store("qa", record("users are people who use the system"));
    storage.store("qa", record("users users users users name"));

    float[] query = embeddings.generateEmbedding("users name");
    List<SearchResult> results = storage.search("qa", query, 3);

    for (int i = 0; i < results.size() - 1; i++) {
      assertThat(results.get(i).score()).isGreaterThanOrEqualTo(results.get(i + 1).score());
    }
  }
}
