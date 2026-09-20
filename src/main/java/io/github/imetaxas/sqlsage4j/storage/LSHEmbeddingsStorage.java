package io.github.imetaxas.sqlsage4j.storage;

import io.github.imetaxas.sqlsage4j.util.VectorMath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embeddings storage using Locality-Sensitive Hashing (LSH) with random hyperplanes. Vectors are
 * hashed into buckets by the sign of their dot product with random hyperplanes. Search checks the
 * matching bucket and neighboring buckets (Hamming distance 1-2) for candidates, then re-ranks by
 * exact cosine similarity.
 */
public final class LSHEmbeddingsStorage implements EmbeddingsStorage {

  private static final int DEFAULT_NUM_HYPERPLANES = 8;
  private static final int DEFAULT_DIMENSIONS = 64;

  private final float[][] hyperplanes;
  private final int numHyperplanes;
  private final Map<String, CollectionData> collections = new ConcurrentHashMap<>();

  public LSHEmbeddingsStorage() {
    this(DEFAULT_NUM_HYPERPLANES, DEFAULT_DIMENSIONS, 42L);
  }

  public LSHEmbeddingsStorage(int numHyperplanes, int dimensions, long seed) {
    this.numHyperplanes = numHyperplanes;
    this.hyperplanes = new float[numHyperplanes][dimensions];
    Random rng = new Random(seed);
    for (int i = 0; i < numHyperplanes; i++) {
      for (int j = 0; j < dimensions; j++) {
        hyperplanes[i][j] = (float) rng.nextGaussian();
      }
    }
  }

  @Override
  public String store(String collection, TrainingRecord record) {
    CollectionData data = collections.computeIfAbsent(collection, k -> new CollectionData());
    int hash = computeHash(record.embedding());
    data.records.put(record.id(), record);
    data.buckets.computeIfAbsent(hash, k -> new ArrayList<>()).add(record.id());
    data.hashes.put(record.id(), hash);
    return record.id();
  }

  @Override
  public List<SearchResult> search(String collection, float[] queryEmbedding, int nResults) {
    CollectionData data = collections.get(collection);
    if (data == null || data.records.isEmpty()) return Collections.emptyList();

    int queryHash = computeHash(queryEmbedding);
    Set<String> candidateIds = new HashSet<>();

    // Exact bucket
    List<String> bucket = data.buckets.getOrDefault(queryHash, Collections.emptyList());
    candidateIds.addAll(bucket);

    // Hamming distance 1 neighbors (flip each bit)
    for (int i = 0; i < numHyperplanes; i++) {
      int neighborHash = queryHash ^ (1 << i);
      candidateIds.addAll(data.buckets.getOrDefault(neighborHash, Collections.emptyList()));
    }

    // Hamming distance 2 neighbors for better recall
    for (int i = 0; i < numHyperplanes; i++) {
      for (int j = i + 1; j < numHyperplanes; j++) {
        int neighborHash = queryHash ^ (1 << i) ^ (1 << j);
        candidateIds.addAll(data.buckets.getOrDefault(neighborHash, Collections.emptyList()));
      }
    }

    // If too few candidates, fall back to brute force
    if (candidateIds.size() < nResults) {
      candidateIds.addAll(data.records.keySet());
    }

    List<SearchResult> results = new ArrayList<>();
    for (String id : candidateIds) {
      TrainingRecord record = data.records.get(id);
      if (record != null) {
        double score = VectorMath.cosineSimilarity(queryEmbedding, record.embedding());
        results.add(new SearchResult(record, score));
      }
    }
    Collections.sort(results);
    return results.subList(0, Math.min(nResults, results.size()));
  }

  @Override
  public List<TrainingRecord> getAll(String collection) {
    CollectionData data = collections.get(collection);
    if (data == null) return Collections.emptyList();
    return List.copyOf(data.records.values());
  }

  @Override
  public boolean remove(String id) {
    for (CollectionData data : collections.values()) {
      if (data.records.containsKey(id)) {
        data.records.remove(id);
        Integer hash = data.hashes.remove(id);
        if (hash != null) {
          List<String> bucket = data.buckets.get(hash);
          if (bucket != null) bucket.remove(id);
        }
        return true;
      }
    }
    return false;
  }

  @Override
  public void removeAll(String collection) {
    collections.remove(collection);
  }

  private int computeHash(float[] vector) {
    int hash = 0;
    for (int i = 0; i < numHyperplanes; i++) {
      double dot = 0;
      int len = Math.min(vector.length, hyperplanes[i].length);
      for (int j = 0; j < len; j++) {
        dot += (double) vector[j] * hyperplanes[i][j];
      }
      if (dot >= 0) hash |= (1 << i);
    }
    return hash;
  }

  private static final class CollectionData {
    final Map<String, TrainingRecord> records = new HashMap<>();
    final Map<Integer, List<String>> buckets = new HashMap<>();
    final Map<String, Integer> hashes = new HashMap<>();
  }
}
