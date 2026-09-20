package io.github.imetaxas.sqlsage4j.util;

public final class VectorMath {

  private VectorMath() {}

  public static double cosineSimilarity(float[] a, float[] b) {
    if (a.length != b.length) {
      throw new IllegalArgumentException(
          "Vector dimensions must match: " + a.length + " vs " + b.length);
    }

    double dot = 0.0;
    double normA = 0.0;
    double normB = 0.0;
    for (int i = 0; i < a.length; i++) {
      dot += (double) a[i] * b[i];
      normA += (double) a[i] * a[i];
      normB += (double) b[i] * b[i];
    }

    double denom = Math.sqrt(normA) * Math.sqrt(normB);
    return denom == 0.0 ? 0.0 : dot / denom;
  }

  public static int approximateTokenCount(String text) {
    return text.length() / 4;
  }
}
