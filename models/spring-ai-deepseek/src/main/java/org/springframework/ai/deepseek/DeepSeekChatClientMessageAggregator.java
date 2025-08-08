package org.springframework.ai.deepseek;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.springframework.ai.chat.client.ChatClientResponse;
import reactor.core.publisher.Flux;

public class DeepSeekChatClientMessageAggregator {

  public Flux<ChatClientResponse> aggregateChatClientResponse(
      Flux<ChatClientResponse> chatClientResponses,
      Consumer<ChatClientResponse> aggregationHandler) {

    AtomicReference<Map<String, Object>> context = new AtomicReference<>(new HashMap<>());

    return new DeepSeekMessageAggregator().aggregate(chatClientResponses.mapNotNull(chatClientResponse -> {
      context.get().putAll(chatClientResponse.context());
      return chatClientResponse.chatResponse();
    }), aggregatedChatResponse -> {
      ChatClientResponse aggregatedChatClientResponse = ChatClientResponse.builder()
          .chatResponse(aggregatedChatResponse).context(context.get()).build();
      aggregationHandler.accept(aggregatedChatClientResponse);
    }).map(chatResponse -> ChatClientResponse.builder().chatResponse(chatResponse)
        .context(context.get()).build());
  }
}
