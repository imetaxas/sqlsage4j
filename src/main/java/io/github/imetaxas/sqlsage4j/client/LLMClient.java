package io.github.imetaxas.sqlsage4j.client;

import io.github.imetaxas.sqlsage4j.ChatMessage;
import java.util.List;

/**
 * Abstraction for Large Language Model providers.
 *
 * <p>Implement this interface to integrate a custom LLM backend (e.g., Anthropic, Gemini, a
 * self-hosted model). Built-in implementations include {@link OpenAIClient} for OpenAI-compatible
 * APIs and {@link OllamaClient} for local Ollama models.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * LLMProviderConfig config = LLMProviderConfig.builder("gpt-4")
 *     .llmClient(myCustomClient)
 *     .build();
 * }</pre>
 */
public interface LLMClient {

  /**
   * Submit a prompt (as a list of chat messages) to the LLM and return the generated text.
   *
   * @param messages ordered list of system, user, and assistant messages forming the conversation
   * @return the model's text response
   */
  String submitPrompt(List<ChatMessage> messages);

  /** Returns the model identifier this client is configured to use (e.g., "gpt-4", "llama3"). */
  String modelName();
}
