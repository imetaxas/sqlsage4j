package io.github.imetaxas.sqlsage4j.client;

import static io.github.imetaxas.realitycheck.RealityAssertions.assertThat;

import io.github.imetaxas.sqlsage4j.ChatMessage;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CachingLLMClientTest {

  @Test
  void cacheHit_returnsCachedResponse() {
    MockLLMClient mock = new MockLLMClient().withResponse("cached result");
    CachingLLMClient client = CachingLLMClient.builder(mock).build();

    List<ChatMessage> messages = List.of(ChatMessage.user("hello"));
    String first = client.submitPrompt(messages);
    String second = client.submitPrompt(messages);

    assertThat(first).isEqualTo("cached result");
    assertThat(second).isEqualTo("cached result");
    assertThat(mock.callCount()).isEqualTo(1);
  }

  @Test
  void differentMessages_callsDelegateAgain() {
    MockLLMClient mock = new MockLLMClient().withResponse("r1").withResponse("r2");
    CachingLLMClient client = CachingLLMClient.builder(mock).build();

    String r1 = client.submitPrompt(List.of(ChatMessage.user("question 1")));
    String r2 = client.submitPrompt(List.of(ChatMessage.user("question 2")));

    assertThat(r1).isEqualTo("r1");
    assertThat(r2).isEqualTo("r2");
    assertThat(mock.callCount()).isEqualTo(2);
  }

  @Test
  void maxEntries_evictsOldest() {
    MockLLMClient mock = new MockLLMClient().withResponse("a").withResponse("b").withResponse("c");
    CachingLLMClient client = CachingLLMClient.builder(mock).maxEntries(2).build();

    client.submitPrompt(List.of(ChatMessage.user("q1")));
    client.submitPrompt(List.of(ChatMessage.user("q2")));
    client.submitPrompt(List.of(ChatMessage.user("q3")));

    assertThat(client.size()).isEqualTo(2);
    assertThat(mock.callCount()).isEqualTo(3);

    mock.withResponse("a-refreshed");
    String refreshed = client.submitPrompt(List.of(ChatMessage.user("q1")));
    assertThat(refreshed).isEqualTo("a-refreshed");
  }

  @Test
  void ttlExpiration_callsDelegateAgain() throws InterruptedException {
    MockLLMClient mock = new MockLLMClient().withResponse("old").withResponse("new");
    CachingLLMClient client = CachingLLMClient.builder(mock).ttl(Duration.ofMillis(50)).build();

    client.submitPrompt(List.of(ChatMessage.user("q")));
    Thread.sleep(80);
    String result = client.submitPrompt(List.of(ChatMessage.user("q")));

    assertThat(result).isEqualTo("new");
    assertThat(mock.callCount()).isEqualTo(2);
  }

  @Test
  void invalidate_clearsCache() {
    MockLLMClient mock = new MockLLMClient().withResponse("v1").withResponse("v2");
    CachingLLMClient client = CachingLLMClient.builder(mock).build();

    client.submitPrompt(List.of(ChatMessage.user("q")));
    assertThat(client.size()).isEqualTo(1);

    client.invalidate();
    assertThat(client.size()).isEqualTo(0);

    client.submitPrompt(List.of(ChatMessage.user("q")));
    assertThat(mock.callCount()).isEqualTo(2);
  }

  @Test
  void delegatesModelName() {
    MockLLMClient mock = new MockLLMClient();
    CachingLLMClient client = CachingLLMClient.builder(mock).build();

    assertThat(client.modelName()).isEqualTo("mock-model");
  }
}
