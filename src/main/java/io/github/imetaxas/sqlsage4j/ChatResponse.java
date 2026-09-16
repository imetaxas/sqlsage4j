package io.github.imetaxas.sqlsage4j;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class ChatResponse {
  ChatResponse() {}

  public abstract String id();

  public abstract String content();

  public static ChatResponse of(String id, String content) {
    return new AutoValue_ChatResponse(id, content);
  }
}
