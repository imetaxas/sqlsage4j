package io.github.imetaxas.sqlsage4j.provider;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class CachingEmbeddingsProviderTest {

  @Test
  void cacheHit_doesNotCallDelegate() {
    AtomicInteger callCount = new AtomicInteger();
    EmbeddingsProvider delegate =
        text -> {
          callCount.incrementAndGet();
          return new float[] {1.0f, 2.0f, 3.0f};
        };

    CachingEmbeddingsProvider cached = new CachingEmbeddingsProvider(delegate);

    float[] first = cached.generateEmbedding("hello");
    float[] second = cached.generateEmbedding("hello");

    assertThat(callCount.get()).isEqualTo(1);
    assertThat(first).hasLength(3);
    assertThat(second).hasLength(3);
  }

  @Test
  void differentText_callsDelegateAgain() {
    AtomicInteger callCount = new AtomicInteger();
    EmbeddingsProvider delegate =
        text -> {
          callCount.incrementAndGet();
          return new float[] {(float) text.length()};
        };

    CachingEmbeddingsProvider cached = new CachingEmbeddingsProvider(delegate);

    cached.generateEmbedding("short");
    cached.generateEmbedding("longer text");

    assertThat(callCount.get()).isEqualTo(2);
  }

  @Test
  void lruEviction_removesOldestEntry() {
    AtomicInteger callCount = new AtomicInteger();
    EmbeddingsProvider delegate =
        text -> {
          callCount.incrementAndGet();
          return new float[] {(float) text.hashCode()};
        };

    CachingEmbeddingsProvider cached = new CachingEmbeddingsProvider(delegate, 2);

    cached.generateEmbedding("a");
    cached.generateEmbedding("b");
    cached.generateEmbedding("c"); // evicts "a"

    assertThat(callCount.get()).isEqualTo(3);

    cached.generateEmbedding("a"); // cache miss, calls delegate
    assertThat(callCount.get()).isEqualTo(4);

    cached.generateEmbedding("c"); // cache hit
    assertThat(callCount.get()).isEqualTo(4);
  }

  @Test
  void defaultMaxSize_handlesManyCalls() {
    AtomicInteger callCount = new AtomicInteger();
    EmbeddingsProvider delegate =
        text -> {
          callCount.incrementAndGet();
          return new float[] {0f};
        };

    CachingEmbeddingsProvider cached = new CachingEmbeddingsProvider(delegate);

    for (int i = 0; i < 100; i++) {
      cached.generateEmbedding("text_" + i);
    }
    assertThat(callCount.get()).isEqualTo(100);

    // All should be cached (default max is 512)
    for (int i = 0; i < 100; i++) {
      cached.generateEmbedding("text_" + i);
    }
    assertThat(callCount.get()).isEqualTo(100);
  }
}
