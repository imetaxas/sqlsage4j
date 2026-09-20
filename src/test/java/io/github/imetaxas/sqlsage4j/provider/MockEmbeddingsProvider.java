package io.github.imetaxas.sqlsage4j.provider;

import java.util.Random;

/**
 * Mock embeddings provider that generates deterministic pseudo-random embeddings based on content
 * hash. Produces embeddings with consistent similarity properties for testing.
 */
public final class MockEmbeddingsProvider implements EmbeddingsProvider {

  private final int dimensions;

  public MockEmbeddingsProvider() {
    this(64);
  }

  public MockEmbeddingsProvider(int dimensions) {
    this.dimensions = dimensions;
  }

  @Override
  public float[] generateEmbedding(String text) {
    Random rng = new Random(text.hashCode());
    float[] embedding = new float[dimensions];
    for (int i = 0; i < dimensions; i++) {
      embedding[i] = rng.nextFloat() * 2 - 1;
    }
    return embedding;
  }
}
