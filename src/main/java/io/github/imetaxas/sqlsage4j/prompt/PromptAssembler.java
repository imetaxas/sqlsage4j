package io.github.imetaxas.sqlsage4j.prompt;

import io.github.imetaxas.sqlsage4j.ChatMessage;
import io.github.imetaxas.sqlsage4j.util.VectorMath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Assembles chat message lists for various prompt types, porting Vanna's prompt construction logic.
 */
public final class PromptAssembler {

  private static final PromptTemplate FOLLOWUP_TEMPLATE =
      PromptTemplate.fromResource("prompts/followup_questions.txt");
  private static final PromptTemplate SUMMARY_TEMPLATE =
      PromptTemplate.fromResource("prompts/summary.txt");
  private static final PromptTemplate REVERSE_QUESTION_TEMPLATE =
      PromptTemplate.fromResource("prompts/reverse_question.txt");
  private static final PromptTemplate QUESTION_REWRITING_TEMPLATE =
      PromptTemplate.fromResource("prompts/question_rewriting.txt");
  private static final PromptTemplate CHART_TEMPLATE =
      PromptTemplate.fromResource("prompts/chart_generation.txt");

  private PromptAssembler() {}

  /** Mirrors Vanna's add_ddl_to_prompt. */
  public static String appendDdls(String prompt, List<String> ddlList, int maxTokens) {
    if (ddlList.isEmpty()) return prompt;
    StringBuilder sb = new StringBuilder(prompt);
    sb.append("\n===Tables \n");
    for (String ddl : ddlList) {
      if (VectorMath.approximateTokenCount(sb.toString()) + VectorMath.approximateTokenCount(ddl)
          < maxTokens) {
        sb.append(ddl).append("\n\n");
      }
    }
    return sb.toString();
  }

  /** Mirrors Vanna's add_documentation_to_prompt. */
  public static String appendDocumentation(String prompt, List<String> docList, int maxTokens) {
    if (docList.isEmpty()) return prompt;
    StringBuilder sb = new StringBuilder(prompt);
    sb.append("\n===Additional Context \n\n");
    for (String doc : docList) {
      if (VectorMath.approximateTokenCount(sb.toString()) + VectorMath.approximateTokenCount(doc)
          < maxTokens) {
        sb.append(doc).append("\n\n");
      }
    }
    return sb.toString();
  }

  /** Mirrors Vanna's generate_followup_questions prompt. */
  public static List<ChatMessage> followupQuestions(
      String question, String sql, String dfMarkdown, int nQuestions) {
    List<ChatMessage> messages = new ArrayList<>();
    messages.add(
        ChatMessage.system(
            "You are a helpful data assistant. The user asked the question: '"
                + question
                + "'\n\nThe SQL query for this question was: "
                + sql
                + "\n\nThe following is a pandas DataFrame with the results of the query: \n"
                + dfMarkdown
                + "\n\n"));
    messages.add(
        ChatMessage.user(FOLLOWUP_TEMPLATE.render(Map.of("n", String.valueOf(nQuestions)))));
    return messages;
  }

  /** Mirrors Vanna's generate_summary prompt. */
  public static List<ChatMessage> summary(String question, String dfMarkdown) {
    List<ChatMessage> messages = new ArrayList<>();
    messages.add(
        ChatMessage.system(
            "You are a helpful data assistant. The user asked the question: '"
                + question
                + "'\n\nThe following is a pandas DataFrame with the results of the query: \n"
                + dfMarkdown
                + "\n\n"));
    messages.add(ChatMessage.user(SUMMARY_TEMPLATE.render()));
    return messages;
  }

  /** Mirrors Vanna's generate_question (reverse: SQL → question). */
  public static List<ChatMessage> reverseQuestion(String sql) {
    List<ChatMessage> messages = new ArrayList<>();
    messages.add(ChatMessage.system(REVERSE_QUESTION_TEMPLATE.render()));
    messages.add(ChatMessage.user(sql));
    return messages;
  }

  /** Mirrors Vanna's generate_rewritten_question for multi-turn. */
  public static List<ChatMessage> rewriteQuestion(String lastQuestion, String newQuestion) {
    List<ChatMessage> messages = new ArrayList<>();
    messages.add(ChatMessage.system(QUESTION_REWRITING_TEMPLATE.render()));
    messages.add(
        ChatMessage.user("First question: " + lastQuestion + "\nSecond question: " + newQuestion));
    return messages;
  }

  /** Mirrors Vanna's generate_plotly_code prompt. */
  public static List<ChatMessage> chartGeneration(String question, String sql, String dfMetadata) {
    String systemMsg =
        "The following is a pandas DataFrame that contains the results of the query"
            + " that answers the question the user asked: '"
            + question
            + "'";
    if (sql != null) {
      systemMsg += "\n\nThe DataFrame was produced using this query: " + sql + "\n\n";
    }
    systemMsg +=
        "The following is information about the resulting pandas DataFrame 'df': \n" + dfMetadata;

    List<ChatMessage> messages = new ArrayList<>();
    messages.add(ChatMessage.system(systemMsg));
    messages.add(ChatMessage.user(CHART_TEMPLATE.render()));
    return messages;
  }
}
