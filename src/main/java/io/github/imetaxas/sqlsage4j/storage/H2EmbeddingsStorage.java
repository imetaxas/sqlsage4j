package io.github.imetaxas.sqlsage4j.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.imetaxas.sqlsage4j.training.TrainingDataType;
import io.github.imetaxas.sqlsage4j.util.VectorMath;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;

/**
 * Embeddings storage backed by H2 in-memory database. Stores vectors as H2 REAL ARRAY columns and
 * computes cosine similarity in Java.
 */
public final class H2EmbeddingsStorage implements EmbeddingsStorage, AutoCloseable {

  private static final Gson GSON = new Gson();
  private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

  private final Connection conn;

  public H2EmbeddingsStorage() {
    try {
      JdbcDataSource ds = new JdbcDataSource();
      ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
      this.conn = ds.getConnection();
      try (Statement stmt = conn.createStatement()) {
        stmt.execute(
            "CREATE TABLE embeddings ("
                + "id VARCHAR(255) PRIMARY KEY, "
                + "collection VARCHAR(255), "
                + "type VARCHAR(50), "
                + "content CLOB, "
                + "embedding REAL ARRAY, "
                + "metadata CLOB)");
        stmt.execute("CREATE INDEX idx_collection ON embeddings(collection)");
      }
    } catch (SQLException e) {
      throw new RuntimeException("H2 init failed", e);
    }
  }

  @Override
  public String store(String collection, TrainingRecord record) {
    try (PreparedStatement ps =
        conn.prepareStatement(
            "INSERT INTO embeddings (id, collection, type, content, embedding, metadata) VALUES (?, ?, ?, ?, ?, ?)")) {
      ps.setString(1, record.id());
      ps.setString(2, collection);
      ps.setString(3, record.type().name());
      ps.setString(4, record.content());
      ps.setArray(5, conn.createArrayOf("REAL", box(record.embedding())));
      ps.setString(6, GSON.toJson(record.metadata()));
      ps.executeUpdate();
      return record.id();
    } catch (SQLException e) {
      throw new RuntimeException("H2 store failed", e);
    }
  }

  @Override
  public List<SearchResult> search(String collection, float[] queryEmbedding, int nResults) {
    List<TrainingRecord> all = getAll(collection);
    if (all.isEmpty()) return Collections.emptyList();

    List<SearchResult> results = new ArrayList<>();
    for (TrainingRecord record : all) {
      double score = VectorMath.cosineSimilarity(queryEmbedding, record.embedding());
      results.add(new SearchResult(record, score));
    }
    Collections.sort(results);
    return results.subList(0, Math.min(nResults, results.size()));
  }

  @Override
  public List<TrainingRecord> getAll(String collection) {
    try (PreparedStatement ps =
        conn.prepareStatement("SELECT * FROM embeddings WHERE collection = ?")) {
      ps.setString(1, collection);
      ResultSet rs = ps.executeQuery();
      List<TrainingRecord> records = new ArrayList<>();
      while (rs.next()) {
        records.add(fromRow(rs));
      }
      return records;
    } catch (SQLException e) {
      throw new RuntimeException("H2 getAll failed", e);
    }
  }

  @Override
  public boolean remove(String id) {
    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM embeddings WHERE id = ?")) {
      ps.setString(1, id);
      return ps.executeUpdate() > 0;
    } catch (SQLException e) {
      throw new RuntimeException("H2 remove failed", e);
    }
  }

  @Override
  public void removeAll(String collection) {
    try (PreparedStatement ps =
        conn.prepareStatement("DELETE FROM embeddings WHERE collection = ?")) {
      ps.setString(1, collection);
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("H2 removeAll failed", e);
    }
  }

  @Override
  public void close() {
    try {
      conn.close();
    } catch (SQLException e) {
      throw new RuntimeException("H2 close failed", e);
    }
  }

  private TrainingRecord fromRow(ResultSet rs) throws SQLException {
    Object[] arr = (Object[]) rs.getArray("embedding").getArray();
    float[] embedding = new float[arr.length];
    for (int i = 0; i < arr.length; i++) embedding[i] = ((Number) arr[i]).floatValue();

    Map<String, String> metadata = GSON.fromJson(rs.getString("metadata"), MAP_TYPE);
    return new TrainingRecord(
        rs.getString("id"),
        TrainingDataType.valueOf(rs.getString("type")),
        rs.getString("content"),
        embedding,
        metadata);
  }

  private static Float[] box(float[] arr) {
    Float[] boxed = new Float[arr.length];
    for (int i = 0; i < arr.length; i++) boxed[i] = arr[i];
    return boxed;
  }
}
