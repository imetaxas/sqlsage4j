package io.github.imetaxas.sqlsage4j.storage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory storage that uses BM25 text similarity for retrieval instead of vector embeddings. This
 * eliminates the need for an external embedding model, making it ideal for local Ollama setups
 * where loading a second model causes memory contention.
 *
 * <p>BM25 (Best Matching 25) is a probabilistic ranking function based on term frequency and
 * inverse document frequency. It works well for keyword matching and short text retrieval.
 *
 * <pre>{@code
 * // Use with NoOpEmbeddingsProvider since embeddings are not needed
 * EmbeddingsStorage storage = new BM25Storage();
 * EmbeddingsProvider provider = new NoOpEmbeddingsProvider();
 * TrainingService service = new TrainingService(provider, storage, llmClient);
 * }</pre>
 */
public final class BM25Storage implements EmbeddingsStorage {

  private static final double K1 = 1.2;
  private static final double B = 0.75;

  private static final Set<String> STOP_WORDS =
      Set.of(
          "a", "an", "the", "is", "are", "was", "were", "be", "been", "being", "have", "has", "had",
          "do", "does", "did", "will", "would", "could", "should", "may", "might", "shall", "can",
          "to", "of", "in", "for", "on", "with", "at", "by", "from", "as", "into", "through",
          "during", "before", "after", "above", "below", "between", "out", "off", "over", "under",
          "again", "further", "then", "once", "and", "but", "or", "nor", "not", "so", "if", "that",
          "this", "it", "i", "we", "you", "they", "he", "she", "me", "my", "your", "his", "her",
          "its", "our", "their", "what", "which", "who", "whom", "how", "when", "where", "why",
          "all", "each", "every", "both", "few", "more", "most", "other", "some", "such", "no",
          "only", "own", "same", "than", "too", "very");

  private final Map<String, List<TrainingRecord>> collections = new ConcurrentHashMap<>();

  @Override
  public String store(String collection, TrainingRecord record) {
    collections.computeIfAbsent(collection, k -> Collections.synchronizedList(new ArrayList<>()));
    collections.get(collection).add(record);
    return record.id();
  }

  @Override
  public List<SearchResult> search(String collection, float[] queryEmbedding, int nResults) {
    List<TrainingRecord> records = collections.getOrDefault(collection, Collections.emptyList());
    if (records.isEmpty()) return Collections.emptyList();

    String queryText = extractQueryText(queryEmbedding);
    if (queryText == null || queryText.isBlank()) {
      return records.subList(0, Math.min(nResults, records.size())).stream()
          .map(r -> new SearchResult(r, 0.0))
          .toList();
    }

    List<String> queryTerms = tokenize(queryText);
    Map<String, Double> idf = computeIdf(records, queryTerms);
    double avgDocLen =
        records.stream().mapToInt(r -> tokenize(r.content()).size()).average().orElse(1);

    List<SearchResult> results = new ArrayList<>();
    for (TrainingRecord record : records) {
      double score = bm25Score(queryTerms, tokenize(record.content()), idf, avgDocLen);
      results.add(new SearchResult(record, score));
    }

    Collections.sort(results);
    return results.subList(0, Math.min(nResults, results.size()));
  }

  @Override
  public List<TrainingRecord> getAll(String collection) {
    return List.copyOf(collections.getOrDefault(collection, Collections.emptyList()));
  }

  @Override
  public boolean remove(String id) {
    for (List<TrainingRecord> records : collections.values()) {
      if (records.removeIf(r -> r.id().equals(id))) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void removeAll(String collection) {
    collections.remove(collection);
  }

  private double bm25Score(
      List<String> queryTerms, List<String> docTerms, Map<String, Double> idf, double avgDocLen) {
    Map<String, Integer> termFreq = new HashMap<>();
    for (String term : docTerms) {
      termFreq.merge(term, 1, Integer::sum);
    }

    double score = 0;
    int docLen = docTerms.size();

    for (String term : queryTerms) {
      int tf = termFreq.getOrDefault(term, 0);
      if (tf == 0) continue;
      double idfVal = idf.getOrDefault(term, 0.0);
      double numerator = tf * (K1 + 1);
      double denominator = tf + K1 * (1 - B + B * docLen / avgDocLen);
      score += idfVal * (numerator / denominator);
    }

    return score;
  }

  private Map<String, Double> computeIdf(List<TrainingRecord> records, List<String> queryTerms) {
    Set<String> queryTermSet = new HashSet<>(queryTerms);
    int n = records.size();

    Map<String, Integer> docFreq = new HashMap<>();
    for (TrainingRecord record : records) {
      Set<String> uniqueTerms = new HashSet<>(tokenize(record.content()));
      for (String term : uniqueTerms) {
        if (queryTermSet.contains(term)) {
          docFreq.merge(term, 1, Integer::sum);
        }
      }
    }

    Map<String, Double> idf = new HashMap<>();
    for (String term : queryTermSet) {
      int df = docFreq.getOrDefault(term, 0);
      idf.put(term, Math.log((n - df + 0.5) / (df + 0.5) + 1));
    }
    return idf;
  }

  static List<String> tokenize(String text) {
    if (text == null || text.isBlank()) return Collections.emptyList();
    return Arrays.stream(text.toLowerCase().split("[^a-z0-9_]+"))
        .filter(t -> !t.isBlank() && t.length() > 1 && !STOP_WORDS.contains(t))
        .toList();
  }

  /**
   * The embedding float array from {@link
   * io.github.imetaxas.sqlsage4j.provider.NoOpEmbeddingsProvider} encodes the original text as char
   * values. This decodes it back.
   */
  @javax.annotation.Nullable
  private String extractQueryText(float[] embedding) {
    if (embedding == null || embedding.length == 0) return null;
    if (embedding.length == 1 && embedding[0] == 0f) return null;
    char[] chars = new char[embedding.length];
    for (int i = 0; i < embedding.length; i++) {
      chars[i] = (char) embedding[i];
    }
    return new String(chars);
  }
}
