package org.springframework.ai.deepseek;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyRateLimit;
import org.springframework.ai.chat.metadata.PromptMetadata;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

/**
 * deepseek消息聚合器
 * lucas
 */
public class DeepSeekMessageAggregator extends MessageAggregator {
	private static final Logger logger = LoggerFactory.getLogger(DeepSeekMessageAggregator.class);
  @Override
  public Flux<ChatResponse> aggregate(Flux<ChatResponse> fluxChatResponse,
      Consumer<ChatResponse> onAggregationComplete) {

    // Assistant Message
    AtomicReference<StringBuilder> messageTextContentRef = new AtomicReference<>(
        new StringBuilder());
    // Reasoning Message
    AtomicReference<StringBuilder> reasoningContentRef = new AtomicReference<>(
        new StringBuilder());
    AtomicReference<Map<String, Object>> messageMetadataMapRef = new AtomicReference<>();

    // ChatGeneration Metadata
    AtomicReference<ChatGenerationMetadata> generationMetadataRef = new AtomicReference<>(
        ChatGenerationMetadata.NULL);

    // Usage
    AtomicReference<Integer> metadataUsagePromptTokensRef = new AtomicReference<Integer>(0);
    AtomicReference<Integer> metadataUsageGenerationTokensRef = new AtomicReference<Integer>(0);
    AtomicReference<Integer> metadataUsageTotalTokensRef = new AtomicReference<Integer>(0);

    AtomicReference<PromptMetadata> metadataPromptMetadataRef = new AtomicReference<>(
        PromptMetadata.empty());
    AtomicReference<RateLimit> metadataRateLimitRef = new AtomicReference<>(new EmptyRateLimit());

    AtomicReference<String> metadataIdRef = new AtomicReference<>("");
    AtomicReference<String> metadataModelRef = new AtomicReference<>("");

    return fluxChatResponse.doOnSubscribe(subscription -> {
      messageTextContentRef.set(new StringBuilder());
      reasoningContentRef.set(new StringBuilder());
      messageMetadataMapRef.set(new HashMap<>());
      metadataIdRef.set("");
      metadataModelRef.set("");
      metadataUsagePromptTokensRef.set(0);
      metadataUsageGenerationTokensRef.set(0);
      metadataUsageTotalTokensRef.set(0);
      metadataPromptMetadataRef.set(PromptMetadata.empty());
      metadataRateLimitRef.set(new EmptyRateLimit());

    }).doOnNext(chatResponse -> {

      if (chatResponse.getResult() != null) {
        if (chatResponse.getResult().getMetadata() != null
            && chatResponse.getResult().getMetadata() != ChatGenerationMetadata.NULL) {
          generationMetadataRef.set(chatResponse.getResult().getMetadata());
        }
        if (chatResponse.getResult().getOutput().getText() != null) {
          messageTextContentRef.get().append(chatResponse.getResult().getOutput().getText());
        }
        if (chatResponse.getResult()
            .getOutput() instanceof DeepSeekAssistantMessage deepSeekAssistantMessage) {
          reasoningContentRef.get().append(deepSeekAssistantMessage.getReasoningContent());
        }
        messageMetadataMapRef.get().putAll(chatResponse.getResult().getOutput().getMetadata());
      }
      if (chatResponse.getMetadata() != null) {
        if (chatResponse.getMetadata().getUsage() != null) {
          Usage usage = chatResponse.getMetadata().getUsage();
          metadataUsagePromptTokensRef.set(
              usage.getPromptTokens() > 0 ? usage.getPromptTokens()
                  : metadataUsagePromptTokensRef.get());
          metadataUsageGenerationTokensRef.set(
              usage.getCompletionTokens() > 0 ? usage.getCompletionTokens()
                  : metadataUsageGenerationTokensRef.get());
          metadataUsageTotalTokensRef
              .set(usage.getTotalTokens() > 0 ? usage.getTotalTokens()
                  : metadataUsageTotalTokensRef.get());
        }
        if (chatResponse.getMetadata().getPromptMetadata() != null
            && chatResponse.getMetadata().getPromptMetadata().iterator().hasNext()) {
          metadataPromptMetadataRef.set(chatResponse.getMetadata().getPromptMetadata());
        }
        if (chatResponse.getMetadata().getRateLimit() != null
            && !(metadataRateLimitRef.get() instanceof EmptyRateLimit)) {
          metadataRateLimitRef.set(chatResponse.getMetadata().getRateLimit());
        }
        if (StringUtils.hasText(chatResponse.getMetadata().getId())) {
          metadataIdRef.set(chatResponse.getMetadata().getId());
        }
        if (StringUtils.hasText(chatResponse.getMetadata().getModel())) {
          metadataModelRef.set(chatResponse.getMetadata().getModel());
        }
      }
    }).doOnComplete(() -> {

      var usage = new DefaultUsage(metadataUsagePromptTokensRef.get(),
          metadataUsageGenerationTokensRef.get(),
          metadataUsageTotalTokensRef.get());

      var chatResponseMetadata = ChatResponseMetadata.builder()
          .id(metadataIdRef.get())
          .model(metadataModelRef.get())
          .rateLimit(metadataRateLimitRef.get())
          .usage(usage)
          .promptMetadata(metadataPromptMetadataRef.get())
          .build();
      onAggregationComplete.accept(new ChatResponse(List.of(new Generation(
          new DeepSeekAssistantMessage(messageTextContentRef.get().toString(),
              reasoningContentRef.get().toString(), messageMetadataMapRef.get()),
          generationMetadataRef.get())), chatResponseMetadata));

      messageTextContentRef.set(new StringBuilder());
      reasoningContentRef.set(new StringBuilder());
      messageMetadataMapRef.set(new HashMap<>());
      metadataIdRef.set("");
      metadataModelRef.set("");
      metadataUsagePromptTokensRef.set(0);
      metadataUsageGenerationTokensRef.set(0);
      metadataUsageTotalTokensRef.set(0);
      metadataPromptMetadataRef.set(PromptMetadata.empty());
      metadataRateLimitRef.set(new EmptyRateLimit());

    }).doOnError(e -> logger.error("Aggregation Error", e));
  }
}
