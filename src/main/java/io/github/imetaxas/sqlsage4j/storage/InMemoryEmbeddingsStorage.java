package io.github.imetaxas.sqlsage4j.storage;

import io.github.imetaxas.sqlsage4j.util.VectorMath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory vector store using cosine similarity. Suitable for testing and small datasets. For
 * production use, replace with ChromaDB or ElasticSearch backed storage.
 */
public final class InMemoryEmbeddingsStorage implements EmbeddingsStorage {

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

    List<SearchResult> results = new ArrayList<>();
    for (TrainingRecord record : records) {
      double score = VectorMath.cosineSimilarity(queryEmbedding, record.embedding());
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
}
