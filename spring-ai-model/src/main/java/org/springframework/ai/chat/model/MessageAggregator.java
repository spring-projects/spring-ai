/*
 * Copyright 2023-2025 the original author or authors.
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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
 * @author Taewoong Kim
 * @since 1.0.0
 */
public class MessageAggregator {

	private static final Logger logger = LoggerFactory.getLogger(MessageAggregator.class);

	public Flux<ChatResponse> aggregate(Flux<ChatResponse> fluxChatResponse,
			Consumer<ChatResponse> onAggregationComplete) {

		// Assistant Message
		AtomicReference<StringBuilder> messageTextContentRef = new AtomicReference<>(new StringBuilder());
		AtomicReference<Map<String, Object>> messageMetadataMapRef = new AtomicReference<>();
		AtomicReference<List<ToolCall>> toolCallsRef = new AtomicReference<>(new ArrayList<>());

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
			messageMetadataMapRef.set(new HashMap<>());
			toolCallsRef.set(new ArrayList<>());
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
				AssistantMessage outputMessage = chatResponse.getResult().getOutput();
				if (!CollectionUtils.isEmpty(outputMessage.getToolCalls())) {
					mergeToolCalls(toolCallsRef.get(), outputMessage.getToolCalls());
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
					toolCallsRef.get().addAll(toolCallsList);
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
			List<ToolCall> collectedToolCalls = toolCallsRef.get();

			if (!CollectionUtils.isEmpty(collectedToolCalls)) {

				finalAssistantMessage = AssistantMessage.builder()
					.content(messageTextContentRef.get().toString())
					.properties(messageMetadataMapRef.get())
					.toolCalls(collectedToolCalls)
					.build();
			}
			else {
				finalAssistantMessage = AssistantMessage.builder()
					.content(messageTextContentRef.get().toString())
					.properties(messageMetadataMapRef.get())
					.build();
			}
			onAggregationComplete.accept(new ChatResponse(List.of(new Generation(finalAssistantMessage,

					generationMetadataRef.get())), chatResponseMetadata));

			messageTextContentRef.set(new StringBuilder());
			messageMetadataMapRef.set(new HashMap<>());
			toolCallsRef.set(new ArrayList<>());
			metadataIdRef.set("");
			metadataModelRef.set("");
			metadataUsagePromptTokensRef.set(0);
			metadataUsageGenerationTokensRef.set(0);
			metadataUsageTotalTokensRef.set(0);
			metadataPromptMetadataRef.set(PromptMetadata.empty());
			metadataRateLimitRef.set(new EmptyRateLimit());

		}).doOnError(e -> logger.error("Aggregation Error", e));
	}

	/**
	 * Merge tool calls by id to handle streaming responses where tool call data is split
	 * across multiple chunks. This is common in OpenAI-compatible APIs like Qwen, where
	 * the first chunk contains the function name and subsequent chunks contain only
	 * arguments. if a tool call has an ID, it's matched by ID. if it has no ID (empty or
	 * null), it's merged with the last tool call in the list.
	 * @param existingToolCalls the list of existing tool calls to merge into
	 * @param newToolCalls the new tool calls to merge
	 */
	private void mergeToolCalls(List<ToolCall> existingToolCalls, List<ToolCall> newToolCalls) {
		for (ToolCall newCall : newToolCalls) {
			if (StringUtils.hasText(newCall.id())) {
				// ID present: match by ID or add as new
				ToolCall existingMatch = existingToolCalls.stream()
					.filter(existing -> newCall.id().equals(existing.id()))
					.findFirst()
					.orElse(null);

				if (existingMatch != null) {
					// Merge with existing tool call with same ID
					int index = existingToolCalls.indexOf(existingMatch);
					ToolCall merged = mergeToolCall(existingMatch, newCall);
					existingToolCalls.set(index, merged);
				}
				else {
					// New tool call with ID
					existingToolCalls.add(newCall);
				}
			}
			else {
				// No ID: merge with last tool call
				ToolCall lastToolCall = existingToolCalls.isEmpty() ? null
						: existingToolCalls.get(existingToolCalls.size() - 1);
				ToolCall merged = mergeToolCall(lastToolCall, newCall);

				if (lastToolCall != null) {
					existingToolCalls.set(existingToolCalls.size() - 1, merged);
				}
				else {
					existingToolCalls.add(merged);
				}
			}
		}
	}

	/**
	 * Merge two tool calls into one, combining their properties.
	 * @param existing the existing tool call
	 * @param current the current tool call to merge
	 * @return the merged tool call
	 */
	private ToolCall mergeToolCall(ToolCall existing, ToolCall current) {
		if (existing == null) {
			return current;
		}

		// Use non-empty ID, prefer existing if both present (for consistency)
		String mergedId = StringUtils.hasText(existing.id()) ? existing.id() : current.id();

		// Use non-empty name, prefer new if both present
		String mergedName = StringUtils.hasText(current.name()) ? current.name() : existing.name();

		// Use non-empty type, prefer new if both present
		String mergedType = StringUtils.hasText(current.type()) ? current.type() : existing.type();

		// Concatenate arguments
		String mergedArgs = (existing.arguments() != null ? existing.arguments() : "")
				+ (current.arguments() != null ? current.arguments() : "");

		return new ToolCall(mergedId, mergedType, mergedName, mergedArgs);
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
