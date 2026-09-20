package io.github.imetaxas.sqlsage4j.storage;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.imetaxas.sqlsage4j.training.TrainingDataType;
import io.github.imetaxas.sqlsage4j.util.VectorMath;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * Embeddings storage backed by Apache Lucene's in-memory KNN vector search. Uses HNSW indexing
 * internally for approximate nearest neighbor search.
 */
public final class LuceneEmbeddingsStorage implements EmbeddingsStorage, AutoCloseable {

  private static final Gson GSON = new Gson();
  private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

  private final Directory directory;
  private final IndexWriter writer;

  public LuceneEmbeddingsStorage() {
    try {
      this.directory = new ByteBuffersDirectory();
      this.writer = new IndexWriter(directory, new IndexWriterConfig());
    } catch (IOException e) {
      throw new RuntimeException("Failed to initialize Lucene index", e);
    }
  }

  @Override
  public String store(String collection, TrainingRecord record) {
    try {
      Document doc = new Document();
      doc.add(new StringField("id", record.id(), Field.Store.YES));
      doc.add(new StringField("collection", collection, Field.Store.YES));
      doc.add(new StoredField("content", record.content()));
      doc.add(new StoredField("type", record.type().name()));
      doc.add(new StoredField("metadata", GSON.toJson(record.metadata())));
      doc.add(new StoredField("embedding_data", toBytes(record.embedding())));
      doc.add(
          new KnnFloatVectorField(
              "embedding", record.embedding(), VectorSimilarityFunction.COSINE));
      writer.addDocument(doc);
      writer.commit();
      return record.id();
    } catch (IOException e) {
      throw new RuntimeException("Lucene store failed", e);
    }
  }

  @Override
  public List<SearchResult> search(String collection, float[] queryEmbedding, int nResults) {
    try {
      if (writer.getDocStats().numDocs == 0) return Collections.emptyList();
      DirectoryReader reader = DirectoryReader.open(writer);
      IndexSearcher searcher = new IndexSearcher(reader);

      KnnFloatVectorQuery knnQuery =
          new KnnFloatVectorQuery(
              "embedding",
              queryEmbedding,
              Math.min(nResults * 2, writer.getDocStats().numDocs),
              new TermQuery(new Term("collection", collection)));

      TopDocs topDocs = searcher.search(knnQuery, nResults);
      List<SearchResult> results = new ArrayList<>();
      for (var scoreDoc : topDocs.scoreDocs) {
        Document doc = searcher.storedFields().document(scoreDoc.doc);
        TrainingRecord record = fromDoc(doc);
        double score = VectorMath.cosineSimilarity(queryEmbedding, record.embedding());
        results.add(new SearchResult(record, score));
      }
      results.sort(null);
      reader.close();
      return results;
    } catch (IOException e) {
      throw new RuntimeException("Lucene search failed", e);
    }
  }

  @Override
  public List<TrainingRecord> getAll(String collection) {
    try {
      if (writer.getDocStats().numDocs == 0) return Collections.emptyList();
      DirectoryReader reader = DirectoryReader.open(writer);
      IndexSearcher searcher = new IndexSearcher(reader);
      TopDocs topDocs =
          searcher.search(new TermQuery(new Term("collection", collection)), Integer.MAX_VALUE);
      List<TrainingRecord> records = new ArrayList<>();
      for (var scoreDoc : topDocs.scoreDocs) {
        records.add(fromDoc(searcher.storedFields().document(scoreDoc.doc)));
      }
      reader.close();
      return records;
    } catch (IOException e) {
      throw new RuntimeException("Lucene getAll failed", e);
    }
  }

  @Override
  public boolean remove(String id) {
    try {
      long before = writer.getDocStats().numDocs;
      writer.deleteDocuments(new Term("id", id));
      writer.commit();
      writer.forceMergeDeletes();
      return writer.getDocStats().numDocs < before;
    } catch (IOException e) {
      throw new RuntimeException("Lucene remove failed", e);
    }
  }

  @Override
  public void removeAll(String collection) {
    try {
      writer.deleteDocuments(new Term("collection", collection));
      writer.commit();
      writer.forceMergeDeletes();
    } catch (IOException e) {
      throw new RuntimeException("Lucene removeAll failed", e);
    }
  }

  @Override
  public void close() {
    try {
      writer.close();
      directory.close();
    } catch (IOException e) {
      throw new RuntimeException("Lucene close failed", e);
    }
  }

  private TrainingRecord fromDoc(Document doc) {
    Map<String, String> metadata = GSON.fromJson(doc.get("metadata"), MAP_TYPE);
    return new TrainingRecord(
        doc.get("id"),
        TrainingDataType.valueOf(doc.get("type")),
        doc.get("content"),
        toFloats(doc.getBinaryValue("embedding_data").bytes),
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
