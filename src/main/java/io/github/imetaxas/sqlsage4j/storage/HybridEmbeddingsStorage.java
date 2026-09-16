package io.github.imetaxas.sqlsage4j.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hybrid retrieval combining vector similarity search with BM25 keyword matching. This improves
 * recall for queries containing exact table/column names that pure vector search may miss.
 *
 * <p>Results are merged using Reciprocal Rank Fusion (RRF): each result's score is {@code alpha /
 * (k + vector_rank) + (1 - alpha) / (k + bm25_rank)}, where {@code k=60} is the standard RRF
 * constant.
 *
 * <pre>{@code
 * EmbeddingsStorage hybrid = new HybridEmbeddingsStorage(
 *     new LuceneEmbeddingsStorage(),  // vector backend
 *     new BM25Storage(),              // keyword backend
 *     0.6                             // 60% weight to vector, 40% to BM25
 * );
 * }</pre>
 */
public final class HybridEmbeddingsStorage implements EmbeddingsStorage {

  private static final int RRF_K = 60;

  private final EmbeddingsStorage vectorStore;
  private final BM25Storage bm25Store;
  private final double alpha;

  /**
   * @param vectorStore any vector-based EmbeddingsStorage implementation
   * @param bm25Store the BM25 keyword-based storage
   * @param alpha blending weight: 1.0 = pure vector, 0.0 = pure BM25 (default: 0.6)
   */
  public HybridEmbeddingsStorage(
      EmbeddingsStorage vectorStore, BM25Storage bm25Store, double alpha) {
    if (alpha < 0 || alpha > 1) {
      throw new IllegalArgumentException("alpha must be between 0.0 and 1.0");
    }
    this.vectorStore = vectorStore;
    this.bm25Store = bm25Store;
    this.alpha = alpha;
  }

  public HybridEmbeddingsStorage(EmbeddingsStorage vectorStore, BM25Storage bm25Store) {
    this(vectorStore, bm25Store, 0.6);
  }

  @Override
  public String store(String collection, TrainingRecord record) {
    vectorStore.store(collection, record);
    bm25Store.store(collection, record);
    return record.id();
  }

  @Override
  public List<SearchResult> search(String collection, float[] queryEmbedding, int nResults) {
    int fetchSize = nResults * 3;

    List<SearchResult> vectorResults = vectorStore.search(collection, queryEmbedding, fetchSize);
    List<SearchResult> bm25Results = bm25Store.search(collection, queryEmbedding, fetchSize);

    return fuseResults(vectorResults, bm25Results, nResults);
  }

  @Override
  public List<TrainingRecord> getAll(String collection) {
    return vectorStore.getAll(collection);
  }

  @Override
  public boolean remove(String id) {
    boolean v = vectorStore.remove(id);
    boolean b = bm25Store.remove(id);
    return v || b;
  }

  @Override
  public void removeAll(String collection) {
    vectorStore.removeAll(collection);
    bm25Store.removeAll(collection);
  }

  private List<SearchResult> fuseResults(
      List<SearchResult> vectorResults, List<SearchResult> bm25Results, int nResults) {
    Map<String, Double> scores = new HashMap<>();
    Map<String, TrainingRecord> records = new HashMap<>();

    for (int i = 0; i < vectorResults.size(); i++) {
      SearchResult r = vectorResults.get(i);
      String id = r.record().id();
      scores.merge(id, alpha / (RRF_K + i + 1), Double::sum);
      records.put(id, r.record());
    }

    for (int i = 0; i < bm25Results.size(); i++) {
      SearchResult r = bm25Results.get(i);
      String id = r.record().id();
      scores.merge(id, (1 - alpha) / (RRF_K + i + 1), Double::sum);
      records.putIfAbsent(id, r.record());
    }

    List<Map.Entry<String, Double>> ranked = new ArrayList<>(scores.entrySet());
    ranked.sort(
        Comparator.<Map.Entry<String, Double>, Double>comparing(Map.Entry::getValue).reversed());

    List<SearchResult> fused = new ArrayList<>();
    for (int i = 0; i < Math.min(nResults, ranked.size()); i++) {
      Map.Entry<String, Double> entry = ranked.get(i);
      fused.add(new SearchResult(records.get(entry.getKey()), entry.getValue()));
    }
    return fused;
  }
}
