import io.github.imetaxas.sqlsage4j.*;
import io.github.imetaxas.sqlsage4j.client.OllamaStreamingClient;
import io.github.imetaxas.sqlsage4j.db.SQLiteConnector;
import io.github.imetaxas.sqlsage4j.enums.PromptEnum;

/**
 * Streaming example — see SQL tokens arrive in real-time.
 *
 * Prerequisites: Ollama running with llama3.1:8b and nomic-embed-text models.
 */
public class StreamingExample {

  public static void main(String[] args) {
    var streamingClient = new OllamaStreamingClient(
        "http://localhost:11434", "llama3.1:8b");

    var sage = SqlSage4j.builder(
            LLMProviderConfig.builder("llama3.1:8b")
                .llmClient(streamingClient)
                .ollamaUrl("http://localhost:11434")
                .maxTokens(4096L)
                .build())
        .databaseConnector(new SQLiteConnector("jdbc:sqlite:chinook.db"))
        .prompt(Prompt.builder().userPrompt(PromptEnum.SQL_EXPERT).build())
        .build();

    var chat = sage.queryChat();
    chat.trainDdl("CREATE TABLE tracks (TrackId INT, Name TEXT, AlbumId INT, Milliseconds INT)");

    // Stream tokens to stdout as they arrive
    System.out.print("Generating SQL: ");
    var response = chat.askStreaming("What is the longest track?", token -> {
      if (!token.finished()) {
        System.out.print(token.text());
      }
    });
    System.out.println("\n\nFinal SQL: " + response.sql());

    // Execute
    var df = chat.run(response);
    System.out.println(df.toText());
  }
}
