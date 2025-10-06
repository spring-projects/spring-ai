package org.springframework.ai.deepseek.advisor;

import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.deepseek.DeepSeekChatClientMessageAggregator;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Flux;

public class DeepSeekSimpleLogAdvisor implements CallAdvisor, StreamAdvisor {

  public static final Function<ChatClientRequest, String> DEFAULT_REQUEST_TO_STRING = ChatClientRequest::toString;

  public static final Function<ChatResponse, String> DEFAULT_RESPONSE_TO_STRING = ModelOptionsUtils::toJsonStringPrettyPrinter;

  private static final Logger logger = LoggerFactory.getLogger(DeepSeekSimpleLogAdvisor.class);

  private final Function<ChatClientRequest, String> requestToString;

  private final Function<ChatResponse, String> responseToString;

  private final int order;

  public DeepSeekSimpleLogAdvisor() {
    this(DEFAULT_REQUEST_TO_STRING, DEFAULT_RESPONSE_TO_STRING, 0);
  }

  public DeepSeekSimpleLogAdvisor(int order) {
    this(DEFAULT_REQUEST_TO_STRING, DEFAULT_RESPONSE_TO_STRING, order);
  }

  public DeepSeekSimpleLogAdvisor(@Nullable Function<ChatClientRequest, String> requestToString,
      @Nullable Function<ChatResponse, String> responseToString, int order) {
    this.requestToString = requestToString != null ? requestToString : DEFAULT_REQUEST_TO_STRING;
    this.responseToString = responseToString != null ? responseToString : DEFAULT_RESPONSE_TO_STRING;
    this.order = order;
  }

  @Override
  public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
    logRequest(chatClientRequest);

    ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);

    logResponse(chatClientResponse);

    return chatClientResponse;
  }

  @Override
  public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
      StreamAdvisorChain streamAdvisorChain) {
    logRequest(chatClientRequest);

    Flux<ChatClientResponse> chatClientResponses = streamAdvisorChain.nextStream(chatClientRequest);

    return new DeepSeekChatClientMessageAggregator().aggregateChatClientResponse(chatClientResponses, this::logResponse);
  }

  private void logRequest(ChatClientRequest request) {
    logger.debug("request: {}", this.requestToString.apply(request));
  }

  private void logResponse(ChatClientResponse chatClientResponse) {
    logger.debug("response: {}", this.responseToString.apply(chatClientResponse.chatResponse()));
  }

  @Override
  public String getName() {
    return this.getClass().getSimpleName();
  }

  @Override
  public int getOrder() {
    return this.order;
  }

  @Override
  public String toString() {
    return DeepSeekSimpleLogAdvisor.class.getSimpleName();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private Function<ChatClientRequest, String> requestToString;

    private Function<ChatResponse, String> responseToString;

    private int order = 0;

    private Builder() {
    }

    public Builder requestToString(Function<ChatClientRequest, String> requestToString) {
      this.requestToString = requestToString;
      return this;
    }

    public Builder responseToString(Function<ChatResponse, String> responseToString) {
      this.responseToString = responseToString;
      return this;
    }

    public Builder order(int order) {
      this.order = order;
      return this;
    }

    public DeepSeekSimpleLogAdvisor build() {
      return new DeepSeekSimpleLogAdvisor(this.requestToString, this.responseToString, this.order);
    }

  }

}
