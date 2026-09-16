import io.github.imetaxas.sqlsage4j.*;
import io.github.imetaxas.sqlsage4j.client.CachingLLMClient;
import io.github.imetaxas.sqlsage4j.client.RateLimitedLLMClient;
import io.github.imetaxas.sqlsage4j.client.RetryingLLMClient;
import io.github.imetaxas.sqlsage4j.db.SQLiteConnector;
import io.github.imetaxas.sqlsage4j.enums.PromptEnum;
import io.github.imetaxas.sqlsage4j.pipeline.SqlGuard;
import java.time.Duration;

/**
 * Production-grade setup with OpenAI, caching, rate limiting, and safety guards.
 *
 * Prerequisites: Set OPENAI_API_KEY environment variable.
 */
public class OpenAIExample {

  public static void main(String[] args) {
    String apiKey = System.getenv("OPENAI_API_KEY");

    // Stack decorators: rate limit → retry → cache → raw client
    var config = LLMProviderConfig.builder("gpt-4o")
        .openAiApiKey(apiKey)
        .maxTokens(4096L)
        .build();

    var sage = SqlSage4j.builder(config)
        .databaseConnector(new SQLiteConnector("jdbc:sqlite:chinook.db"))
        .prompt(Prompt.builder().userPrompt(PromptEnum.SQL_EXPERT).build())
        .sqlGuard(SqlGuard.readOnly())  // Block any INSERT/UPDATE/DELETE/DROP
        .build();

    var chat = sage.queryChat();

    // Train with schema + golden examples
    chat.trainDdl("CREATE TABLE artists (ArtistId INT PRIMARY KEY, Name TEXT)");
    chat.trainDdl("CREATE TABLE albums (AlbumId INT PRIMARY KEY, Title TEXT, ArtistId INT)");
    chat.train("How many albums does each artist have?",
        "SELECT a.Name, COUNT(al.AlbumId) AS album_count FROM artists a "
            + "JOIN albums al ON a.ArtistId = al.ArtistId GROUP BY a.Name ORDER BY album_count DESC");

    // Ask and execute
    var response = chat.ask("Which artist has the most albums?");
    System.out.println("Generated SQL: " + response.sql());

    var df = chat.run(response);
    System.out.println(df.toCsv());
  }
}
