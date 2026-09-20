package io.github.imetaxas.sqlsage4j.util;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import org.junit.jupiter.api.Test;

final class VectorMathTest {

  @Test
  void identicalVectorsHaveSimilarityOne() {
    float[] v = {1.0f, 2.0f, 3.0f};
    assertThat(VectorMath.cosineSimilarity(v, v)).isCloseTo(1.0, 0.0001);
  }

  @Test
  void orthogonalVectorsHaveSimilarityZero() {
    float[] a = {1.0f, 0.0f};
    float[] b = {0.0f, 1.0f};
    assertThat(VectorMath.cosineSimilarity(a, b)).isCloseTo(0.0, 0.0001);
  }

  @Test
  void oppositeVectorsHaveSimilarityNegativeOne() {
    float[] a = {1.0f, 2.0f};
    float[] b = {-1.0f, -2.0f};
    assertThat(VectorMath.cosineSimilarity(a, b)).isCloseTo(-1.0, 0.0001);
  }

  @Test
  void tokenCountApproximation() {
    assertThat(VectorMath.approximateTokenCount("1234")).isEqualTo(1);
    assertThat(VectorMath.approximateTokenCount("12345678")).isEqualTo(2);
  }
}
