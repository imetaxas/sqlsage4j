package io.github.imetaxas.benchmark;

import java.util.List;

public record TrainingData(
    List<String> ddls,
    List<QuestionAnswer> questionAnswers,
    List<String> documentation) {

  public record QuestionAnswer(String question, String sql) {}
}
