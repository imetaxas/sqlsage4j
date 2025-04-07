# `queryfy`  [![status](https://tingle-api.spotify.net/v1/badge/yanimetaxas/queryfy/status)](https://backstage.spotify.net/components/queryfy/tingle) ![version](https://tingle-api.spotify.net/v1/badge/yanimetaxas/queryfy/version)

Java RAG library for text-2-query, text-2-chat and LLM diff

# Reqs

    1. Can be imported as a library
    2. Able to use different solutions for storing 
        a.   chromadb, ES, GCS
    3. Able to train different documents: 
        a. queries, 
        b. DDLS
        c. documents (pdf, json, text, Word, PPT, PDF, EXCEL, PPT, Markdown, HTML)
    4. Able to train different documents in different databases or tables (separating domains)
    5. Able to use different embedders
        a. OpenAI
    6. Able to use different models for chatting
        a. OpenAI, Gemini, Anthropic, other open source multi-models (llama3.2)
    7. Able to use different models for plotting
    8. Able to use different prompts with different structure
        a. queries 
        b. DDLs
        c. question - answer tuples
        d. Ontologies (https://arxiv.org/pdf/2311.07509)
        e. Documents - enums
    9. Chat with multi-turn conversations (threads)
    10. Support re-ranking
    11. Support vector search algorithms
        a. cosine-similarity
        b. Neirest-neighbour
    12. Search embeddings in different stores
    13. Store embeddings in different stores
    14. Export results: 
        a. json, csv, plot diagram, text
    15. Run response query (if managed to connect)
    16. Use ENV variables for configuration
    17. Have sample UI
    18. Can run multiple LLM calls and save results to a storage

# RAG Runners

## QueryChat
```java
import com.spotify.queryfy.Queryfy;
    
enum EmbeddingsSearchAlgorithm {
  COSINE_SIMILARITY, SPOTIFY_VOYAGER
}
enum RerankingAlgorithm {
  // https://arxiv.org/pdf/2304.09542
  GPT_RANKING,
  // https://arxiv.org/pdf/2306.17563
  Pairwise_Ranking
}
public static void main(String[] args) throws Exception {
  // Find queries from data
  QueryChat chat = Queryfy.builder(
          LLMProviderConfigBuilder().builder("model-name")
              .embeddingsProvider(
                  new OpenAIEmbedder()
              )
              .embeddingsStorage(
                  new ElasticSearchStorage()
                      .setEmbeddingsSearchAlgorithm(EmbeddingsSearchAlgorithm.SPOTIFY_VOYAGER)
              )
              .diagramsPlotProvider(
                  new OpenAIEmbedder()
              )
              .serviceAccount("/user/credentials/SA.json")
              .apiKey("api-key")
              .maxTokens(2048)
              .temperature(0.7)
              .build())
      .train(new List(new Document("./2022 Enterprise Plan.pdf")))
      .train(new List(new Document("./2023 Enterprise Plan.pdf"),
          new Document("./2024 Enterprise Plan.pdf")))
      .connectToStorage(Storage.BIGQUERY)
      .prompt(PromptBuilder.builder()
          .userPrompt(PROMPTS.DATA_SCIENTIST)
          .systemPrompt(PROMPTS.SYSTEM)
          .assistantPrompt(PROMPTS.ASSISTANT)
          .sampleQuestionsAnswers(List.<QuestionAnswer>of())
          .sampleDocuments(List.<SampleDocument>of())
          .ontologies(List.<Ontology>of())
          .ddls(List.<DDL>of())
          .queries(List.<String>of)
          .build())
      .reranking(RerunkingAlgorithmBuilder.builder()
          .algorithm(RerankingAlgorithm.GPT_RANKING)
          .build())
      .QueryChat();

  QueryResponse query = chat.ask("Return 10 top most viewed articles").getQueryResponse();
  System.out.println("Response: " + query);

  QueryExecutionDetails runExecutionDetails = query.run();
  System.out.println("Run execution details: " + runExecutionDetails);

  Plot plot = query.plot();
  System.out.println("Plot: " + plot);

  query.exportAs(Type.JSON, "/path-to-export");

  query.train(query.getQuestion(), query);

  List<String> questions = chat.generateQuestionsFromData();
  System.out.println("Suggested questions: " + questions);

  String data = chat.getAllTrainedData();
  System.out.println("Current trained data: " + data);

  String datum = chat.getTrainedData(dataId);
  System.out.println("Trained data with id " + dataId + ", data=" + datum);

  chat.deleteTrainedData(dataId);
  chat.deleteAllTrainedData();
}
```
## LLMChat
```java
import com.spotify.queryfy.Queryfy;
  
public static void main(String[] args) throws Exception {
  // chat anything
  LLMChat chat = Queryfy.builder(
      LLMProviderConfigBuilder().builder("model-name")
          .embeddingsProvider(
              new OpenAIEmbedder()
          )
          .embeddingsStorage(
              new ElasticSearchStorage()
                  .setEmbeddingsSearchAlgorithm(EmbeddingsSearchAlgorithm.SPOTIFY_VOYAGER)
          )
          .serviceAccount("/user/credentials/SA.json")
          .apiKey("api-key")
          .maxTokens(2048)
          .temperature(0.7)
          .build())
      .train(new List(new Document("./2022 Enterprise Plan.pdf")))
      .train(new List(new Document("./2023 Enterprise Plan.pdf"), new Document("./2024 Enterprise Plan.pdf")))
      .prompt(PromptBuilder.builder()
          .userPrompt(PROMPTS.DATA_SCIENTIST)
          .systemPrompt(PROMPTS.SYSTEM)
          .assistantPrompt(PROMPTS.ASSISTANT)
          .sampleDocuments(List.<SampleDocument>of())
          .build())
      .LLMChat();

  ChatResponse response = chat.ask("Which is the capital of Sweden?").getChatResponse();
  System.out.println("Response: " + response);

  response = chat.ask("Which is the capital of Norway?").getChatResponse();
  System.out.println("Response: " + response);
}
```
## LLMDiff
```java
import com.spotify.queryfy.Queryfy;
  
public static void main(String[] args) throws Exception {
  LLMDiff llmDiff = LLMDiffBuilder().builder(queryfy1, queryfy2)
      .ask("Return 10 top most viewed articles")
      .times(10)
      .run();
  
  // show tables of resulys from queryfy1 and queryfy2 and their differences
  System.out.println("Diff results: " + llmDiff);

  llmDiff.exportAs(Type.PDF, "/path-to-export/file.pdf");
  llmDiff.exportAs(Type.JPG, "/path-to-export/file.jpg");
}
```

    

