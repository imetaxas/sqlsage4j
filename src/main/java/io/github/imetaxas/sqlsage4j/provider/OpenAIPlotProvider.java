package io.github.imetaxas.sqlsage4j.provider;

import io.github.imetaxas.sqlsage4j.ChatMessage;
import io.github.imetaxas.sqlsage4j.client.LLMClient;
import io.github.imetaxas.sqlsage4j.db.DataFrame;
import io.github.imetaxas.sqlsage4j.prompt.PromptAssembler;
import java.util.List;

public final class OpenAIPlotProvider implements DiagramsPlotProvider {

  private final LLMClient llmClient;

  public OpenAIPlotProvider(LLMClient llmClient) {
    this.llmClient = llmClient;
  }

  @Override
  public String generatePlotCode(String question, String sql, DataFrame df) {
    String dfMetadata =
        "Columns: "
            + String.join(", ", df.columns())
            + "\nRows: "
            + df.rowCount()
            + "\nSample:\n"
            + df.head(5).toMarkdown();

    List<ChatMessage> prompt = PromptAssembler.chartGeneration(question, sql, dfMetadata);
    String response = llmClient.submitPrompt(prompt);

    return extractPythonCode(response);
  }

  private String extractPythonCode(String response) {
    String trimmed = response.trim();
    if (trimmed.contains("```python")) {
      int start = trimmed.indexOf("```python") + 9;
      int end = trimmed.indexOf("```", start);
      if (end > start) {
        return trimmed.substring(start, end).trim();
      }
    }
    if (trimmed.contains("```")) {
      int start = trimmed.indexOf("```") + 3;
      int end = trimmed.indexOf("```", start);
      if (end > start) {
        return trimmed.substring(start, end).trim();
      }
    }
    return trimmed;
  }
}
