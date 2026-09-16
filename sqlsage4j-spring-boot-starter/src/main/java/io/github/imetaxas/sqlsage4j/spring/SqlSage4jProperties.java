package io.github.imetaxas.sqlsage4j.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for sqlsage4j Spring Boot integration.
 *
 * <p>Example {@code application.yml}:
 *
 * <pre>
 * sqlsage4j:
 *   model-name: llama3
 *   base-url: http://localhost:11434
 *   max-tokens: 4096
 *   temperature: 0.0
 *   embeddings:
 *     provider: ollama
 *     base-url: http://localhost:11434
 *   storage:
 *     type: in-memory
 *   safety:
 *     read-only: true
 * </pre>
 */
@ConfigurationProperties(prefix = "sqlsage4j")
public class SqlSage4jProperties {

  private boolean enabled = true;
  private String modelName;
  private String apiKey;
  private String baseUrl;
  private long maxTokens = 4096L;
  private double temperature = 0.0;

  private Embeddings embeddings = new Embeddings();
  private Storage storage = new Storage();
  private Safety safety = new Safety();
  private Database database = new Database();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getModelName() {
    return modelName;
  }

  public void setModelName(String modelName) {
    this.modelName = modelName;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public long getMaxTokens() {
    return maxTokens;
  }

  public void setMaxTokens(long maxTokens) {
    this.maxTokens = maxTokens;
  }

  public double getTemperature() {
    return temperature;
  }

  public void setTemperature(double temperature) {
    this.temperature = temperature;
  }

  public Embeddings getEmbeddings() {
    return embeddings;
  }

  public void setEmbeddings(Embeddings embeddings) {
    this.embeddings = embeddings;
  }

  public Storage getStorage() {
    return storage;
  }

  public void setStorage(Storage storage) {
    this.storage = storage;
  }

  public Safety getSafety() {
    return safety;
  }

  public void setSafety(Safety safety) {
    this.safety = safety;
  }

  public Database getDatabase() {
    return database;
  }

  public void setDatabase(Database database) {
    this.database = database;
  }

  public static class Embeddings {
    private String provider = "openai";
    private String model = "text-embedding-3-small";
    private String baseUrl;
    private String apiKey;

    public String getProvider() {
      return provider;
    }

    public void setProvider(String provider) {
      this.provider = provider;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }
  }

  public static class Storage {
    private String type = "in-memory";

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }
  }

  public static class Safety {
    private boolean readOnly = false;

    public boolean isReadOnly() {
      return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
      this.readOnly = readOnly;
    }
  }

  public static class Database {
    private String dialect;
    private String dsn;

    public String getDialect() {
      return dialect;
    }

    public void setDialect(String dialect) {
      this.dialect = dialect;
    }

    public String getDsn() {
      return dsn;
    }

    public void setDsn(String dsn) {
      this.dsn = dsn;
    }
  }
}
