package io.github.imetaxas.benchmark;

import javax.annotation.Nullable;

public record BenchmarkQuestion(
    String id,
    int tier,
    String question,
    String goldSql,
    @Nullable String expectedBehavior) {

  public boolean isRefusal() {
    return "__REFUSE__".equals(goldSql);
  }
}
