package io.github.imetaxas.sqlsage4j;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import org.junit.jupiter.api.Test;

final class ChatMessageTest {

  @Test
  void factoryMethods() {
    ChatMessage sys = ChatMessage.system("hello");
    assertThat(sys.role()).as("system role").isEqualTo(ChatRole.SYSTEM);
    assertThat(sys.content()).isEqualTo("hello");

    ChatMessage usr = ChatMessage.user("question");
    assertThat(usr.role()).as("user role").isEqualTo(ChatRole.USER);

    ChatMessage asst = ChatMessage.assistant("answer");
    assertThat(asst.role()).as("assistant role").isEqualTo(ChatRole.ASSISTANT);
  }

  @Test
  void equality() {
    ChatMessage a = ChatMessage.system("hello");
    ChatMessage b = ChatMessage.system("hello");
    assertThat(a).as("equal messages").isEqualTo(b);
    assertThat(a.hashCode()).isEqualTo(b.hashCode());
  }

  @Test
  void inequality() {
    ChatMessage a = ChatMessage.system("hello");
    ChatMessage b = ChatMessage.user("hello");
    assertThat(a).as("different roles").isNotEqualTo(b);
  }
}
