package io.github.imetaxas.sqlsage4j.client;

import io.github.imetaxas.sqlsage4j.ChatMessage;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/** Mock LLM client for testing. Returns canned responses and records all prompts submitted. */
public final class MockLLMClient implements LLMClient {

  private final Queue<String> cannedResponses = new LinkedList<>();
  private final List<List<ChatMessage>> submittedPrompts = new ArrayList<>();
  private String defaultResponse = "SELECT 1;";

  public MockLLMClient() {}

  public MockLLMClient withResponse(String response) {
    cannedResponses.add(response);
    return this;
  }

  public MockLLMClient withDefaultResponse(String response) {
    this.defaultResponse = response;
    return this;
  }

  @Override
  public String submitPrompt(List<ChatMessage> messages) {
    submittedPrompts.add(List.copyOf(messages));
    return cannedResponses.isEmpty() ? defaultResponse : cannedResponses.poll();
  }

  @Override
  public String modelName() {
    return "mock-model";
  }

  public List<List<ChatMessage>> submittedPrompts() {
    return submittedPrompts;
  }

  public int callCount() {
    return submittedPrompts.size();
  }

  public List<ChatMessage> lastPrompt() {
    return submittedPrompts.get(submittedPrompts.size() - 1);
  }
}
