package io.github.imetaxas.sqlsage4j;

import java.util.Objects;

public final class ChatMessage {

  private final ChatRole role;
  private final String content;

  private ChatMessage(ChatRole role, String content) {
    this.role = Objects.requireNonNull(role);
    this.content = Objects.requireNonNull(content);
  }

  public static ChatMessage system(String content) {
    return new ChatMessage(ChatRole.SYSTEM, content);
  }

  public static ChatMessage user(String content) {
    return new ChatMessage(ChatRole.USER, content);
  }

  public static ChatMessage assistant(String content) {
    return new ChatMessage(ChatRole.ASSISTANT, content);
  }

  public ChatRole role() {
    return role;
  }

  public String content() {
    return content;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ChatMessage that)) return false;
    return role == that.role && content.equals(that.content);
  }

  @Override
  public int hashCode() {
    return Objects.hash(role, content);
  }

  @Override
  public String toString() {
    return "ChatMessage{role=" + role + ", content='" + content + "'}";
  }
}
