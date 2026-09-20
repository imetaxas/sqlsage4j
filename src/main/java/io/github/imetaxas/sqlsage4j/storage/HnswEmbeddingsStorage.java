package io.github.imetaxas.sqlsage4j.storage;

import io.github.imetaxas.sqlsage4j.util.VectorMath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Embeddings storage using a pure Java Navigable Small World (NSW) graph for approximate nearest
 * neighbor search. Each node is connected to its M closest neighbors, enabling greedy graph
 * traversal at search time.
 */
public final class HnswEmbeddingsStorage implements EmbeddingsStorage {

  private static final int DEFAULT_MAX_CONNECTIONS = 16;
  private static final int DEFAULT_EF_SEARCH = 64;

  private final int maxConnections;
  private final int efSearch;
  private final Map<String, Graph> graphs = new ConcurrentHashMap<>();

  public HnswEmbeddingsStorage() {
    this(DEFAULT_MAX_CONNECTIONS, DEFAULT_EF_SEARCH);
  }

  public HnswEmbeddingsStorage(int maxConnections, int efSearch) {
    this.maxConnections = maxConnections;
    this.efSearch = efSearch;
  }

  @Override
  public String store(String collection, TrainingRecord record) {
    Graph graph = graphs.computeIfAbsent(collection, k -> new Graph());
    graph.insert(record, maxConnections);
    return record.id();
  }

  @Override
  public List<SearchResult> search(String collection, float[] queryEmbedding, int nResults) {
    Graph graph = graphs.get(collection);
    if (graph == null || graph.isEmpty()) return Collections.emptyList();
    return graph.search(queryEmbedding, nResults, efSearch);
  }

  @Override
  public List<TrainingRecord> getAll(String collection) {
    Graph graph = graphs.get(collection);
    if (graph == null) return Collections.emptyList();
    return List.copyOf(graph.records.values());
  }

  @Override
  public boolean remove(String id) {
    for (Graph graph : graphs.values()) {
      if (graph.remove(id)) return true;
    }
    return false;
  }

  @Override
  public void removeAll(String collection) {
    graphs.remove(collection);
  }

  private static final class Graph {
    final Map<String, TrainingRecord> records = new HashMap<>();
    final Map<String, Set<String>> adjacency = new HashMap<>();
    String entryPoint = null;

    boolean isEmpty() {
      return records.isEmpty();
    }

    void insert(TrainingRecord record, int maxConn) {
      String id = record.id();
      records.put(id, record);
      adjacency.put(id, new HashSet<>());

      if (entryPoint == null) {
        entryPoint = id;
        return;
      }

      List<String> nearest = findNearest(record.embedding(), maxConn, records.size());
      for (String neighborId : nearest) {
        connect(id, neighborId);
        pruneConnections(neighborId, maxConn);
      }
    }

    boolean remove(String id) {
      if (!records.containsKey(id)) return false;
      Set<String> neighbors = adjacency.remove(id);
      records.remove(id);
      if (neighbors != null) {
        for (String n : neighbors) {
          Set<String> adj = adjacency.get(n);
          if (adj != null) adj.remove(id);
        }
      }
      if (id.equals(entryPoint)) {
        entryPoint = records.isEmpty() ? null : records.keySet().iterator().next();
      }
      return true;
    }

    List<SearchResult> search(float[] query, int k, int ef) {
      if (records.isEmpty()) return Collections.emptyList();

      Set<String> visited = new HashSet<>();
      PriorityQueue<ScoredNode> candidates =
          new PriorityQueue<>(Comparator.comparingDouble(n -> -n.score));
      PriorityQueue<ScoredNode> results =
          new PriorityQueue<>(Comparator.comparingDouble(n -> n.score));

      double startScore = similarity(query, entryPoint);
      candidates.add(new ScoredNode(entryPoint, startScore));
      results.add(new ScoredNode(entryPoint, startScore));
      visited.add(entryPoint);

      while (!candidates.isEmpty()) {
        ScoredNode current = candidates.poll();
        double worstResult = results.size() >= ef ? results.peek().score : -Double.MAX_VALUE;
        if (current.score < worstResult) break;

        Set<String> neighbors = adjacency.getOrDefault(current.id, Collections.emptySet());
        for (String neighborId : neighbors) {
          if (visited.contains(neighborId)) continue;
          visited.add(neighborId);

          double score = similarity(query, neighborId);
          if (results.size() < ef || score > results.peek().score) {
            candidates.add(new ScoredNode(neighborId, score));
            results.add(new ScoredNode(neighborId, score));
            if (results.size() > ef) results.poll();
          }
        }
      }

      List<SearchResult> output = new ArrayList<>();
      while (!results.isEmpty()) {
        ScoredNode node = results.poll();
        output.add(new SearchResult(records.get(node.id), node.score));
      }
      output.sort(null);
      return output.subList(0, Math.min(k, output.size()));
    }

    private List<String> findNearest(float[] query, int k, int maxScan) {
      List<ScoredNode> scored = new ArrayList<>();
      for (Map.Entry<String, TrainingRecord> entry : records.entrySet()) {
        double score = VectorMath.cosineSimilarity(query, entry.getValue().embedding());
        scored.add(new ScoredNode(entry.getKey(), score));
      }
      scored.sort(Comparator.comparingDouble(n -> -n.score));
      return scored.subList(0, Math.min(k, scored.size())).stream().map(n -> n.id).toList();
    }

    private void connect(String a, String b) {
      adjacency.get(a).add(b);
      adjacency.computeIfAbsent(b, k -> new HashSet<>()).add(a);
    }

    private void pruneConnections(String id, int maxConn) {
      Set<String> neighbors = adjacency.get(id);
      if (neighbors == null || neighbors.size() <= maxConn) return;

      TrainingRecord record = records.get(id);
      List<ScoredNode> scored = new ArrayList<>();
      for (String nId : neighbors) {
        scored.add(
            new ScoredNode(
                nId,
                VectorMath.cosineSimilarity(record.embedding(), records.get(nId).embedding())));
      }
      scored.sort(Comparator.comparingDouble(n -> -n.score));

      Set<String> kept = new HashSet<>();
      for (int i = 0; i < maxConn && i < scored.size(); i++) {
        kept.add(scored.get(i).id);
      }
      neighbors.retainAll(kept);
    }

    private double similarity(float[] query, String id) {
      return VectorMath.cosineSimilarity(query, records.get(id).embedding());
    }

    private record ScoredNode(String id, double score) {}
  }
}
