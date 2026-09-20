package io.github.imetaxas.sqlsage4j.client;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.ChatMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

final class StreamingClientTest {

  @Test
  void streamToken_of_createsNonFinishedToken() {
    StreamToken token = StreamToken.of("SELECT");

    assertThat(token.text()).isEqualTo("SELECT");
    assertThat(token.finished()).isFalse();
    assertThat(token.promptTokens()).isNull();
    assertThat(token.completionTokens()).isNull();
    assertThat(token.totalTokens()).isNull();
  }

  @Test
  void streamToken_done_createsFinishedToken() {
    StreamToken token = StreamToken.done();

    assertThat(token.text()).isEqualTo("");
    assertThat(token.finished()).isTrue();
  }

  @Test
  void streamToken_doneWithUsage_includesTokenCounts() {
    StreamToken token = StreamToken.done(100, 50);

    assertThat(token.finished()).isTrue();
    assertThat(token.promptTokens()).isEqualTo(100L);
    assertThat(token.completionTokens()).isEqualTo(50L);
    assertThat(token.totalTokens()).isEqualTo(150L);
  }

  @Test
  void mockStreamingClient_streamPromptReturnsTokens() {
    MockStreamingLLMClient mock = new MockStreamingLLMClient("SELECT ", "COUNT(*)", " FROM users");

    List<String> collected = new ArrayList<>();
    try (Stream<StreamToken> tokens = mock.streamPrompt(List.of(ChatMessage.user("count users")))) {
      tokens.forEach(t -> collected.add(t.text()));
    }

    assertThat(collected).hasSize(4);
    assertThat(collected.get(0)).isEqualTo("SELECT ");
    assertThat(collected.get(1)).isEqualTo("COUNT(*)");
    assertThat(collected.get(2)).isEqualTo(" FROM users");
    assertThat(collected.get(3)).as("done token").isEqualTo("");
  }

  @Test
  void mockStreamingClient_callbackAssemblesFullResponse() {
    MockStreamingLLMClient mock = new MockStreamingLLMClient("SELECT ", "1");

    AtomicInteger tokenCount = new AtomicInteger();
    String full =
        mock.streamPrompt(List.of(ChatMessage.user("test")), token -> tokenCount.incrementAndGet());

    assertThat(full).isEqualTo("SELECT 1");
    assertThat(tokenCount.get()).isEqualTo(3);
  }

  @Test
  void mockStreamingClient_submitPromptReturnsFullText() {
    MockStreamingLLMClient mock = new MockStreamingLLMClient("SELECT ", "* ", "FROM t");

    String result = mock.submitPrompt(List.of(ChatMessage.user("test")));

    assertThat(result).isEqualTo("SELECT * FROM t");
  }

  @Test
  void mockStreamingClient_modelNameReturnsConfigured() {
    MockStreamingLLMClient mock = new MockStreamingLLMClient("x");
    assertThat(mock.modelName()).isEqualTo("mock-streaming");
  }

  @Test
  void streamingClient_streamUsedWithTryWithResources_closesCleanly() {
    MockStreamingLLMClient mock = new MockStreamingLLMClient("a", "b", "c");

    StringBuilder sb = new StringBuilder();
    try (Stream<StreamToken> tokens = mock.streamPrompt(List.of(ChatMessage.user("q")))) {
      tokens.filter(t -> !t.finished()).forEach(t -> sb.append(t.text()));
    }
    assertThat(sb.toString()).isEqualTo("abc");
  }

  @Test
  void streamingInterface_defaultCallbackMethod_worksCorrectly() {
    List<StreamToken> received = new ArrayList<>();
    MockStreamingLLMClient mock = new MockStreamingLLMClient("hello", " world");

    String result = mock.streamPrompt(List.of(ChatMessage.user("test")), received::add);

    assertThat(result).isEqualTo("hello world");
    assertThat(received).hasSize(3);
    assertThat(received.get(2).finished()).isTrue();
  }

  /** Test-only implementation of StreamingLLMClient for unit testing. */
  static final class MockStreamingLLMClient implements StreamingLLMClient {

    private final List<String> chunks;

    MockStreamingLLMClient(String... chunks) {
      this.chunks = List.of(chunks);
    }

    @Override
    public Stream<StreamToken> streamPrompt(List<ChatMessage> messages) {
      List<StreamToken> tokens = new ArrayList<>();
      for (String chunk : chunks) {
        tokens.add(StreamToken.of(chunk));
      }
      tokens.add(StreamToken.done());
      return tokens.stream();
    }

    @Override
    public String submitPrompt(List<ChatMessage> messages) {
      StringBuilder sb = new StringBuilder();
      try (Stream<StreamToken> tokens = streamPrompt(messages)) {
        tokens.forEach(t -> sb.append(t.text()));
      }
      return sb.toString();
    }

    @Override
    public String modelName() {
      return "mock-streaming";
    }
  }
}
