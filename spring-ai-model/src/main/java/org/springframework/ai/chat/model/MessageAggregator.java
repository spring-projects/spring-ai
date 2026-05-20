/*
 * Copyright 2023-present the original author or authors.
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyRateLimit;
import org.springframework.ai.chat.metadata.PromptMetadata;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Helper that for streaming chat responses, aggregate the chat response messages into a
 * single AssistantMessage. Job is performed in parallel to the chat response processing.
 *
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @author Thomas Vitale
 * @author Heonwoo Kim
 * @since 1.0.0
 */
public class MessageAggregator {

	private static final Logger logger = LoggerFactory.getLogger(MessageAggregator.class);

	public Flux<ChatResponse> aggregate(Flux<ChatResponse> fluxChatResponse,
			Consumer<ChatResponse> onAggregationComplete) {

		// Assistant Message
		AtomicReference<StringBuilder> messageTextContentRef = new AtomicReference<>(new StringBuilder());
		AtomicReference<StringBuilder> thoughtsRef = new AtomicReference<>(new StringBuilder());
		AtomicReference<StringBuilder> outputWithoutThoughtsRef = new AtomicReference<>(new StringBuilder());
		AtomicReference<Map<String, Object>> messageMetadataMapRef = new AtomicReference<>();
		AtomicReference<LinkedHashMap<String, ToolCallBuilder>> toolCallBuildersRef = new AtomicReference<>(
				new LinkedHashMap<>());
		AtomicInteger anonymousToolCallCounterRef = new AtomicInteger();

		// ChatGeneration Metadata
		AtomicReference<ChatGenerationMetadata> generationMetadataRef = new AtomicReference<>(
				ChatGenerationMetadata.NULL);

		// Usage
		AtomicReference<Integer> metadataUsagePromptTokensRef = new AtomicReference<>(0);
		AtomicReference<Integer> metadataUsageGenerationTokensRef = new AtomicReference<>(0);
		AtomicReference<Integer> metadataUsageTotalTokensRef = new AtomicReference<>(0);

		AtomicReference<PromptMetadata> metadataPromptMetadataRef = new AtomicReference<>(PromptMetadata.empty());
		AtomicReference<RateLimit> metadataRateLimitRef = new AtomicReference<>(new EmptyRateLimit());

		AtomicReference<String> metadataIdRef = new AtomicReference<>("");
		AtomicReference<String> metadataModelRef = new AtomicReference<>("");

		return fluxChatResponse.doOnSubscribe(subscription -> {
			messageTextContentRef.set(new StringBuilder());
			thoughtsRef.set(new StringBuilder());
			outputWithoutThoughtsRef.set(new StringBuilder());
			messageMetadataMapRef.set(new HashMap<>());
			toolCallBuildersRef.set(new LinkedHashMap<>());
			anonymousToolCallCounterRef.set(0);
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
					var metadata = chatResponse.getResult().getOutput().getMetadata();
					if (metadata != null && metadata.containsKey("isThought")) {
						var isThought = Boolean.parseBoolean(metadata.get("isThought").toString());
						if (isThought) {
							thoughtsRef.get().append(chatResponse.getResult().getOutput().getText());
						}
						else {
							outputWithoutThoughtsRef.get().append(chatResponse.getResult().getOutput().getText());
						}
					}
				}
				if (chatResponse.getResult().getOutput().getMetadata() != null) {
					messageMetadataMapRef.get().putAll(chatResponse.getResult().getOutput().getMetadata());
				}
				AssistantMessage outputMessage = chatResponse.getResult().getOutput();
				if (!CollectionUtils.isEmpty(outputMessage.getToolCalls())) {
					mergeToolCalls(outputMessage.getToolCalls(), toolCallBuildersRef.get(),
							anonymousToolCallCounterRef);
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
				Object toolCallsFromMetadata = chatResponse.getMetadata().get("toolCalls");
				if (toolCallsFromMetadata instanceof List) {
					@SuppressWarnings("unchecked")
					List<ToolCall> toolCallsList = (List<ToolCall>) toolCallsFromMetadata;
					mergeToolCalls(toolCallsList, toolCallBuildersRef.get(), anonymousToolCallCounterRef);
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

			AssistantMessage finalAssistantMessage;
			var messageMetadata = messageMetadataMapRef.get();
			if (!thoughtsRef.get().isEmpty()) {
				messageMetadata.put("thoughts", thoughtsRef.get().toString());
				messageMetadata.put("outputWithoutThoughts", outputWithoutThoughtsRef.get().toString());
			}
			List<ToolCall> collectedToolCalls = toolCallBuildersRef.get()
				.values()
				.stream()
				.map(ToolCallBuilder::build)
				.toList();

			if (!CollectionUtils.isEmpty(collectedToolCalls)) {

				finalAssistantMessage = AssistantMessage.builder()
					.content(messageTextContentRef.get().toString())
					.properties(messageMetadata)
					.toolCalls(collectedToolCalls)
					.build();
			}
			else {
				finalAssistantMessage = AssistantMessage.builder()
					.content(messageTextContentRef.get().toString())
					.properties(messageMetadata)
					.build();
			}
			onAggregationComplete.accept(new ChatResponse(List.of(new Generation(finalAssistantMessage,

					generationMetadataRef.get())), chatResponseMetadata));

			messageTextContentRef.set(new StringBuilder());
			thoughtsRef.set(new StringBuilder());
			outputWithoutThoughtsRef.set(new StringBuilder());
			messageMetadataMapRef.set(new HashMap<>());
			toolCallBuildersRef.set(new LinkedHashMap<>());
			anonymousToolCallCounterRef.set(0);
			metadataIdRef.set("");
			metadataModelRef.set("");
			metadataUsagePromptTokensRef.set(0);
			metadataUsageGenerationTokensRef.set(0);
			metadataUsageTotalTokensRef.set(0);
			metadataPromptMetadataRef.set(PromptMetadata.empty());
			metadataRateLimitRef.set(new EmptyRateLimit());

		}).doOnError(e -> logger.error("Aggregation Error", e));
	}

	private static void mergeToolCalls(List<ToolCall> incoming, LinkedHashMap<String, ToolCallBuilder> builders,
			AtomicInteger anonymousCounter) {
		for (ToolCall toolCall : incoming) {
			String key = StringUtils.hasText(toolCall.id()) ? toolCall.id()
					: "_anon_" + anonymousCounter.getAndIncrement();
			builders.computeIfAbsent(key, k -> new ToolCallBuilder()).merge(toolCall);
		}
	}

	private static final class ToolCallBuilder {

		private @Nullable String id;

		private @Nullable String type;

		private @Nullable String name;

		private final StringBuilder arguments = new StringBuilder();

		private boolean finalized;

		void merge(ToolCall toolCall) {
			if (StringUtils.hasText(toolCall.id())) {
				this.id = toolCall.id();
			}
			if (StringUtils.hasText(toolCall.type())) {
				this.type = toolCall.type();
			}
			if (StringUtils.hasText(toolCall.name())) {
				this.name = toolCall.name();
			}
			if (!toolCall.partial()) {
				// Provider has emitted the complete arguments string in this frame —
				// trust it as authoritative and discard whatever fragments accumulated.
				this.arguments.setLength(0);
				if (toolCall.arguments() != null) {
					this.arguments.append(toolCall.arguments());
				}
				this.finalized = true;
			}
			else if (!this.finalized) {
				if (toolCall.arguments() != null) {
					this.arguments.append(toolCall.arguments());
				}
			}
		}

		ToolCall build() {
			return new ToolCall(this.id != null ? this.id : "", this.type != null ? this.type : "function",
					this.name != null ? this.name : "", this.arguments.toString(), false);
		}

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
