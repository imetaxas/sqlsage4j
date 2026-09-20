package io.github.imetaxas.sqlsage4j.pipeline;

import io.github.imetaxas.sqlsage4j.ChatMessage;
import java.util.List;

/**
 * Callback interface for observing pipeline execution stages.
 *
 * <p>Implement this to hook in metrics, tracing, or structured logging at each stage of SQL
 * generation. All methods have default no-op implementations so you only override what you need.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * PipelineListener metricsListener = new PipelineListener() {
 *     @Override
 *     public void onLLMResponse(String response, long latencyMs) {
 *         metrics.timer("llm.latency").record(latencyMs, TimeUnit.MILLISECONDS);
 *     }
 * };
 *
 * SqlSage4j.builder(config)
 *     .pipelineListener(metricsListener)
 *     .build();
 * }</pre>
 */
public interface PipelineListener {

  /** Called after context retrieval from the vector store. */
  default void onContextRetrieved(int qaCount, int ddlCount, int docCount) {}

  /** Called after the prompt is fully assembled, before sending to the LLM. */
  default void onPromptAssembled(List<ChatMessage> messages, int estimatedTokens) {}

  /** Called after the LLM returns a response. */
  default void onLLMResponse(String response, long latencyMs) {}

  /** Called after SQL is extracted from the LLM response. */
  default void onSqlExtracted(String sql) {}

  /** Called when SQL validation fails and a retry is attempted. */
  default void onValidationRetry(String sql, String error, int attempt) {}

  /** Called when the full pipeline completes (success or failure). */
  default void onPipelineComplete(String question, boolean success, long totalLatencyMs) {}

  /** A no-op listener that does nothing. */
  PipelineListener NOOP = new PipelineListener() {};
}
