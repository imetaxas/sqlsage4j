package io.github.imetaxas.sqlsage4j.client;

import javax.annotation.Nullable;

/**
 * A single token/chunk from a streaming LLM response.
 *
 * <p>Each token contains a text fragment. The final token in a stream has {@code finished = true}
 * and may include usage statistics.
 *
 * <pre>{@code
 * client.streamPrompt(messages).forEach(token -> {
 *     System.out.print(token.text());
 *     if (token.finished()) {
 *         System.out.println("\n[" + token.totalTokens() + " tokens]");
 *     }
 * });
 * }</pre>
 */
public record StreamToken(
    String text,
    boolean finished,
    @Nullable Long promptTokens,
    @Nullable Long completionTokens,
    @Nullable Long totalTokens) {

  public static StreamToken of(String text) {
    return new StreamToken(text, false, null, null, null);
  }

  public static StreamToken done() {
    return new StreamToken("", true, null, null, null);
  }

  public static StreamToken done(long promptTokens, long completionTokens) {
    return new StreamToken(
        "", true, promptTokens, completionTokens, promptTokens + completionTokens);
  }
}
