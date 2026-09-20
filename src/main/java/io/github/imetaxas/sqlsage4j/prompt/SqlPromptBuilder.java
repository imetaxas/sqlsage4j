package io.github.imetaxas.sqlsage4j.prompt;

import io.github.imetaxas.sqlsage4j.ChatMessage;
import io.github.imetaxas.sqlsage4j.QuestionAnswer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Builds the SQL generation prompt. Assembles: system prompt + DDLs + documentation + response
 * guidelines + few-shot Q&amp;A pairs (as multi-turn messages) + user question.
 *
 * <p>Q&amp;A examples are sent as separate user/assistant message turns to teach the model the
 * expected concise output format (SQL-only responses).
 */
public final class SqlPromptBuilder {

  private static final PromptTemplate SYSTEM_TEMPLATE =
      PromptTemplate.fromResource("prompts/sql_generation_system.txt");
  private static final PromptTemplate GUIDELINES_TEMPLATE =
      PromptTemplate.fromResource("prompts/sql_response_guidelines.txt");

  private SqlPromptBuilder() {}

  public static List<ChatMessage> build(
      @Nullable String initialPrompt,
      String question,
      List<QuestionAnswer> questionSqlPairs,
      List<String> ddlList,
      List<String> docList,
      String dialect,
      int maxTokens) {

    Map<String, String> vars = Map.of("dialect", dialect);
    String systemPrompt = initialPrompt != null ? initialPrompt : SYSTEM_TEMPLATE.render(vars);

    systemPrompt = PromptAssembler.appendDdls(systemPrompt, ddlList, maxTokens);
    systemPrompt = PromptAssembler.appendDocumentation(systemPrompt, docList, maxTokens);
    systemPrompt += GUIDELINES_TEMPLATE.render(vars);

    List<ChatMessage> messages = new ArrayList<>();
    messages.add(ChatMessage.system(systemPrompt));

    for (QuestionAnswer qa : questionSqlPairs) {
      messages.add(ChatMessage.user(qa.question()));
      messages.add(ChatMessage.assistant(qa.sql()));
    }

    messages.add(ChatMessage.user(question));
    return messages;
  }
}
