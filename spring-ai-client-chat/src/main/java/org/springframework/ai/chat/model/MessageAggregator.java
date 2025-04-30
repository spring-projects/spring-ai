/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.chat.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyRateLimit;
import org.springframework.ai.chat.metadata.PromptMetadata;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.util.StringUtils;

/**
 * Helper that for streaming chat responses, aggregate the chat response messages into a
 * single AssistantMessage. Job is performed in parallel to the chat response processing.
 *
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @since 1.0.0
 */
public class MessageAggregator {

	private static final Logger logger = LoggerFactory.getLogger(MessageAggregator.class);

	public Flux<AdvisedResponse> aggregateAdvisedResponse(Flux<AdvisedResponse> advisedResponses,
			Consumer<AdvisedResponse> aggregationHandler) {

		AtomicReference<Map<String, Object>> adviseContext = new AtomicReference<>(new HashMap<>());

		return new MessageAggregator().aggregate(advisedResponses.map(ar -> {
			adviseContext.get().putAll(ar.adviseContext());
			return ar.response();

		}), aggregatedChatResponse -> {

			AdvisedResponse aggregatedAdvisedResponse = AdvisedResponse.builder()
				.response(aggregatedChatResponse)
				.adviseContext(adviseContext.get())
				.build();

			aggregationHandler.accept(aggregatedAdvisedResponse);

		}).map(cr -> new AdvisedResponse(cr, adviseContext.get()));
	}

	public Flux<ChatResponse> aggregate(Flux<ChatResponse> fluxChatResponse,
			Consumer<ChatResponse> onAggregationComplete) {

		// Assistant Message
		AtomicReference<StringBuilder> messageTextContentRef = new AtomicReference<>(new StringBuilder());
		AtomicReference<Map<String, Object>> messageMetadataMapRef = new AtomicReference<>();

		// ChatGeneration Metadata
		AtomicReference<ChatGenerationMetadata> generationMetadataRef = new AtomicReference<>(
				ChatGenerationMetadata.NULL);

		// Usage
		AtomicReference<Integer> metadataUsagePromptTokensRef = new AtomicReference<Integer>(0);
		AtomicReference<Integer> metadataUsageGenerationTokensRef = new AtomicReference<Integer>(0);
		AtomicReference<Integer> metadataUsageTotalTokensRef = new AtomicReference<Integer>(0);

		AtomicReference<PromptMetadata> metadataPromptMetadataRef = new AtomicReference<>(PromptMetadata.empty());
		AtomicReference<RateLimit> metadataRateLimitRef = new AtomicReference<>(new EmptyRateLimit());

		AtomicReference<String> metadataIdRef = new AtomicReference<>("");
		AtomicReference<String> metadataModelRef = new AtomicReference<>("");

		return fluxChatResponse.doOnSubscribe(subscription -> {
			messageTextContentRef.set(new StringBuilder());
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
				if (chatResponse.getResult().getOutput().getMetadata() != null) {
					messageMetadataMapRef.get().putAll(chatResponse.getResult().getOutput().getMetadata());
				}
			}
			if (chatResponse.getMetadata() != null) {
				if (chatResponse.getMetadata().getUsage() != null) {
					Usage usage = chatResponse.getMetadata().getUsage();
					metadataUsagePromptTokensRef.set(
							usage.getPromptTokens() > 0 ? usage.getPromptTokens() : metadataUsagePromptTokensRef.get());
					metadataUsageGenerationTokensRef.set(usage.getCompletionTokens() > 0 ? usage.getCompletionTokens()
							: metadataUsageGenerationTokensRef.get());
					metadataUsageTotalTokensRef
						.set(usage.getTotalTokens() > 0 ? usage.getTotalTokens() : metadataUsageTotalTokensRef.get());
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

			var usage = new DefaultUsage(metadataUsagePromptTokensRef.get(), metadataUsageGenerationTokensRef.get(),
					metadataUsageTotalTokensRef.get());

			var chatResponseMetadata = ChatResponseMetadata.builder()
				.id(metadataIdRef.get())
				.model(metadataModelRef.get())
				.rateLimit(metadataRateLimitRef.get())
				.usage(usage)
				.promptMetadata(metadataPromptMetadataRef.get())
				.build();

			onAggregationComplete.accept(new ChatResponse(List.of(new Generation(
					new AssistantMessage(messageTextContentRef.get().toString(), messageMetadataMapRef.get()),
					generationMetadataRef.get())), chatResponseMetadata));

			messageTextContentRef.set(new StringBuilder());
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

	public record DefaultUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) implements Usage {

		@Override
		public Integer getPromptTokens() {
			return promptTokens();
		}

		@Override
		public Integer getCompletionTokens() {
			return completionTokens();
		}

		@Override
		public Integer getTotalTokens() {
			return totalTokens();
		}

		@Override
		public Map<String, Integer> getNativeUsage() {
			Map<String, Integer> usage = new HashMap<>();
			usage.put("promptTokens", promptTokens());
			usage.put("completionTokens", completionTokens());
			usage.put("totalTokens", totalTokens());
			return usage;
		}
	}

}
