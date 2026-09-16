package io.github.imetaxas.sqlsage4j.storage;

import java.util.List;

/**
 * Vector storage backend for training data retrieval.
 *
 * <p>Stores training records (DDLs, Q&amp;A pairs, documentation) with their embeddings and
 * retrieves the most similar records for a given query vector. This is the core of the RAG
 * pipeline.
 *
 * <p>Built-in implementations:
 *
 * <ul>
 *   <li>{@link InMemoryEmbeddingsStorage} — simple in-memory store (good for prototyping)
 *   <li>{@link LuceneEmbeddingsStorage} — Apache Lucene HNSW index
 *   <li>{@link HnswEmbeddingsStorage} — pure-Java HNSW graph
 *   <li>{@link LSHEmbeddingsStorage} — locality-sensitive hashing
 *   <li>{@link H2EmbeddingsStorage} — H2 database-backed
 *   <li>{@link SQLiteEmbeddingsStorage} — SQLite database-backed
 *   <li>{@link BM25Storage} — keyword-based retrieval (no embeddings needed)
 * </ul>
 */
public interface EmbeddingsStorage {

  /**
   * Store a training record in the given collection.
   *
   * @param collection the collection name (e.g., "ddl", "question_sql", "documentation")
   * @param record the training record with its embedding vector
   * @return the ID assigned to the stored record
   */
  String store(String collection, TrainingRecord record);

  /**
   * Find the most similar records to a query vector.
   *
   * @param collection the collection to search within
   * @param queryEmbedding the query vector to compare against stored embeddings
   * @param nResults maximum number of results to return
   * @return records ordered by descending similarity score
   */
  List<SearchResult> search(String collection, float[] queryEmbedding, int nResults);

  /**
   * Retrieve all records in a collection.
   *
   * @param collection the collection name
   * @return all stored training records (unordered)
   */
  List<TrainingRecord> getAll(String collection);

  /**
   * Remove a single record by ID.
   *
   * @param id the record identifier returned by {@link #store}
   * @return {@code true} if the record was found and removed
   */
  boolean remove(String id);

  /**
   * Remove all records in a collection.
   *
   * @param collection the collection to clear
   */
  void removeAll(String collection);
}
