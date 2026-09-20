package io.github.imetaxas.sqlsage4j.provider;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import org.junit.jupiter.api.Test;

final class NoOpEmbeddingsProviderTest {

  private final NoOpEmbeddingsProvider provider = new NoOpEmbeddingsProvider();

  @Test
  void encodesTextAsCharValues() {
    float[] result = provider.generateEmbedding("ABC");

    assertThat(result).hasLength(3);
    assertThat(result[0]).isEqualTo(65f); // 'A'
    assertThat(result[1]).isEqualTo(66f); // 'B'
    assertThat(result[2]).isEqualTo(67f); // 'C'
  }

  @Test
  void nullText_returnsSingleZero() {
    float[] result = provider.generateEmbedding(null);
    assertThat(result).hasLength(1);
    assertThat(result[0]).isEqualTo(0f);
  }

  @Test
  void emptyText_returnsSingleZero() {
    float[] result = provider.generateEmbedding("");
    assertThat(result).hasLength(1);
    assertThat(result[0]).isEqualTo(0f);
  }

  @Test
  void roundTrip_canDecodeBackToOriginalText() {
    String original = "Hello World";
    float[] encoded = provider.generateEmbedding(original);

    char[] chars = new char[encoded.length];
    for (int i = 0; i < encoded.length; i++) {
      chars[i] = (char) encoded[i];
    }
    String decoded = new String(chars);

    assertThat(decoded).isEqualTo(original);
  }

  @Test
  void lengthMatchesInputLength() {
    String text = "SELECT COUNT(*) FROM users";
    float[] result = provider.generateEmbedding(text);
    assertThat(result).hasLength(text.length());
  }
}
