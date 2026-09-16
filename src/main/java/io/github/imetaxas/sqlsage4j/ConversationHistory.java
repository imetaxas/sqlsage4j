package io.github.imetaxas.sqlsage4j;

import io.github.imetaxas.sqlsage4j.db.DataFrame;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Stores conversation state per interaction ID. Mirrors Vanna's MemoryCache. */
public final class ConversationHistory {

  private final Map<String, Entry> entries = new LinkedHashMap<>();

  public void set(String id, String question, @Nullable String sql, @Nullable DataFrame df) {
    entries.put(id, new Entry(question, sql, df));
  }

  @Nullable
  public Entry get(String id) {
    return entries.get(id);
  }

  @Nullable
  public String getLastQuestion() {
    if (entries.isEmpty()) return null;
    List<Entry> values = new ArrayList<>(entries.values());
    return values.get(values.size() - 1).question();
  }

  public List<Map.Entry<String, Entry>> getAll() {
    return Collections.unmodifiableList(new ArrayList<>(entries.entrySet()));
  }

  public void clear() {
    entries.clear();
  }

  public static final class Entry {
    private final String question;
    @Nullable private final String sql;
    @Nullable private final DataFrame df;

    Entry(String question, @Nullable String sql, @Nullable DataFrame df) {
      this.question = question;
      this.sql = sql;
      this.df = df;
    }

    public String question() {
      return question;
    }

    @Nullable
    public String sql() {
      return sql;
    }

    @Nullable
    public DataFrame df() {
      return df;
    }
  }
}
