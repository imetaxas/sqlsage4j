package io.github.imetaxas.sqlsage4j.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.imetaxas.sqlsage4j.training.TrainingDataType;
import io.github.imetaxas.sqlsage4j.util.VectorMath;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.sqlite.SQLiteDataSource;

/**
 * Embeddings storage backed by SQLite in-memory database. Stores vectors as BLOBs (serialized float
 * arrays) and computes cosine similarity in Java.
 */
public final class SQLiteEmbeddingsStorage implements EmbeddingsStorage, AutoCloseable {

  private static final Gson GSON = new Gson();
  private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

  private final Connection conn;

  public SQLiteEmbeddingsStorage() {
    try {
      SQLiteDataSource ds = new SQLiteDataSource();
      ds.setUrl("jdbc:sqlite::memory:");
      this.conn = ds.getConnection();
      try (Statement stmt = conn.createStatement()) {
        stmt.execute(
            "CREATE TABLE embeddings ("
                + "id TEXT PRIMARY KEY, "
                + "collection TEXT NOT NULL, "
                + "type TEXT NOT NULL, "
                + "content TEXT, "
                + "embedding BLOB, "
                + "metadata TEXT)");
        stmt.execute("CREATE INDEX idx_collection ON embeddings(collection)");
      }
    } catch (SQLException e) {
      throw new RuntimeException("SQLite init failed", e);
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
      ps.setBytes(5, toBytes(record.embedding()));
      ps.setString(6, GSON.toJson(record.metadata()));
      ps.executeUpdate();
      return record.id();
    } catch (SQLException e) {
      throw new RuntimeException("SQLite store failed", e);
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
      throw new RuntimeException("SQLite getAll failed", e);
    }
  }

  @Override
  public boolean remove(String id) {
    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM embeddings WHERE id = ?")) {
      ps.setString(1, id);
      return ps.executeUpdate() > 0;
    } catch (SQLException e) {
      throw new RuntimeException("SQLite remove failed", e);
    }
  }

  @Override
  public void removeAll(String collection) {
    try (PreparedStatement ps =
        conn.prepareStatement("DELETE FROM embeddings WHERE collection = ?")) {
      ps.setString(1, collection);
      ps.executeUpdate();
    } catch (SQLException e) {
      throw new RuntimeException("SQLite removeAll failed", e);
    }
  }

  @Override
  public void close() {
    try {
      conn.close();
    } catch (SQLException e) {
      throw new RuntimeException("SQLite close failed", e);
    }
  }

  private TrainingRecord fromRow(ResultSet rs) throws SQLException {
    Map<String, String> metadata = GSON.fromJson(rs.getString("metadata"), MAP_TYPE);
    return new TrainingRecord(
        rs.getString("id"),
        TrainingDataType.valueOf(rs.getString("type")),
        rs.getString("content"),
        toFloats(rs.getBytes("embedding")),
        metadata);
  }

  private static byte[] toBytes(float[] arr) {
    ByteBuffer buf = ByteBuffer.allocate(arr.length * 4);
    for (float f : arr) buf.putFloat(f);
    return buf.array();
  }

  private static float[] toFloats(byte[] bytes) {
    ByteBuffer buf = ByteBuffer.wrap(bytes);
    float[] arr = new float[bytes.length / 4];
    for (int i = 0; i < arr.length; i++) arr[i] = buf.getFloat();
    return arr;
  }
}
