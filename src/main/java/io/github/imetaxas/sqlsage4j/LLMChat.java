package io.github.imetaxas.sqlsage4j;

import io.github.imetaxas.sqlsage4j.client.LLMClient;
import io.github.imetaxas.sqlsage4j.training.TrainingService;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * General-purpose RAG chat — retrieves relevant documentation from the vector store and uses it as
 * context for free-form conversations.
 */
public final class LLMChat {

  private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

  private final LLMClient llmClient;
  private final TrainingService trainingService;
  private final List<ChatMessage> conversationHistory;
  private final String systemPrompt;

  LLMChat(LLMClient llmClient, TrainingService trainingService, String systemPrompt) {
    this.llmClient = llmClient;
    this.trainingService = trainingService;
    this.conversationHistory = new ArrayList<>();
    this.systemPrompt = systemPrompt;
  }

  /** Ask a question with RAG context from trained documentation. */
  public ChatResponse ask(String question) {
    logger.info("LLMChat question: {}", question);

    List<String> relatedDocs = trainingService.getRelatedDocumentation(question);
    List<String> relatedDdl = trainingService.getRelatedDdl(question);

    String contextBlock = buildContextBlock(relatedDocs, relatedDdl);

    List<ChatMessage> messages = new ArrayList<>();
    String fullSystemPrompt = systemPrompt + (contextBlock.isEmpty() ? "" : "\n\n" + contextBlock);
    messages.add(ChatMessage.system(fullSystemPrompt));

    messages.addAll(conversationHistory);
    messages.add(ChatMessage.user(question));

    String response = llmClient.submitPrompt(messages);

    conversationHistory.add(ChatMessage.user(question));
    conversationHistory.add(ChatMessage.assistant(response));

    return ChatResponse.of(UUID.randomUUID().toString(), response);
  }

  public List<ChatMessage> getHistory() {
    return List.copyOf(conversationHistory);
  }

  public void clearHistory() {
    conversationHistory.clear();
  }

  private String buildContextBlock(List<String> docs, List<String> ddls) {
    StringBuilder sb = new StringBuilder();
    if (!ddls.isEmpty()) {
      sb.append("===Relevant Schema\n");
      ddls.forEach(d -> sb.append(d).append("\n\n"));
    }
    if (!docs.isEmpty()) {
      sb.append("===Relevant Documentation\n");
      docs.forEach(d -> sb.append(d).append("\n\n"));
    }
    return sb.toString();
  }
}
