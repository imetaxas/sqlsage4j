import io.github.imetaxas.sqlsage4j.*;
import io.github.imetaxas.sqlsage4j.db.SQLiteConnector;
import io.github.imetaxas.sqlsage4j.enums.PromptEnum;

/**
 * Minimal sqlsage4j example — ask a question in English, get SQL + results.
 *
 * Prerequisites:
 *   1. Ollama running locally (brew install ollama && ollama pull llama3.1:8b)
 *   2. A SQLite database file (or any JDBC-compatible database)
 *
 * Run: javac -cp sqlsage4j-0.1.0.jar QuickStart.java && java -cp .:sqlsage4j-0.1.0.jar QuickStart
 */
public class QuickStart {

  public static void main(String[] args) {
    // 1. Point at your database
    var connector = new SQLiteConnector("jdbc:sqlite:chinook.db");

    // 2. Build sqlsage4j with Ollama (local, free, no API key needed)
    var sage = SqlSage4j.builder(
            LLMProviderConfig.builder("llama3.1:8b")
                .ollamaUrl("http://localhost:11434")
                .maxTokens(4096L)
                .build())
        .databaseConnector(connector)
        .prompt(Prompt.builder().userPrompt(PromptEnum.SQL_EXPERT).build())
        .build();

    var chat = sage.queryChat();

    // 3. Auto-discover schema from the live database (no manual DDLs!)
    chat.trainFromDatabase(connector.getDataSource());

    // 4. Ask a question in plain English
    var response = chat.ask("What are the top 5 artists by number of albums?");
    System.out.println("SQL: " + response.sql());
    System.out.println("Confidence: " + (int)(response.confidence() * 100) + "%");

    // 5. Execute and print results
    var results = chat.run(response);
    System.out.println(results.toText());
  }
}
