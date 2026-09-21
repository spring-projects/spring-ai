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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.OpaquePayload;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.StreamingParts;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolCallPart;
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
 * <p>
 * Parts stamped with a content block index (see {@link StreamingParts}) are merged per
 * index: deltas are appended, a complete part replaces the slot. Indexed parts are
 * grouped by the response id carried in {@code ChatResponseMetadata}; groups keep the
 * order in which they were first seen and parts are ordered by index within a group. When
 * a chat model does not stamp a response id, a new group starts whenever the block index
 * goes backwards or a slot that already holds content receives a part it cannot continue.
 * Parts without an index follow the legacy behavior: text is concatenated into one part,
 * tool calls are appended, reasoning and unknown parts are carried in arrival order,
 * media on chunks is not carried.
 *
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @author Thomas Vitale
 * @author Heonwoo Kim
 * @since 1.0.0
 */
public class MessageAggregator {

	private static final Log logger = LogFactory.getLog(MessageAggregator.class);

	public Flux<ChatResponse> aggregate(Flux<ChatResponse> fluxChatResponse,
			Consumer<ChatResponse> onAggregationComplete) {

		// Assistant Message
		AtomicReference<StringBuilder> messageTextContentRef = new AtomicReference<>(new StringBuilder());
		// Deprecated "isThought" routing of unindexed text chunks, kept for one release.
		AtomicReference<StringBuilder> thoughtsRef = new AtomicReference<>(new StringBuilder());
		AtomicReference<StringBuilder> outputWithoutThoughtsRef = new AtomicReference<>(new StringBuilder());
		AtomicReference<Map<String, Object>> messageMetadataMapRef = new AtomicReference<>();
		AtomicReference<List<ToolCall>> toolCallsRef = new AtomicReference<>(new ArrayList<>());
		// Unindexed parts other than text, tool calls and media, in arrival order.
		AtomicReference<List<MessagePart>> carriedPartsRef = new AtomicReference<>(new ArrayList<>());
		// Indexed parts: one group per response, in first-seen order.
		AtomicReference<List<IndexedGroup>> indexedGroupsRef = new AtomicReference<>(new ArrayList<>());

		// ChatGeneration Metadata
		AtomicReference<ChatGenerationMetadata> generationMetadataRef = new AtomicReference<>(
				ChatGenerationMetadata.NULL);

		// Usage
		AtomicReference<Integer> metadataUsagePromptTokensRef = new AtomicReference<>(0);
		AtomicReference<Integer> metadataUsageGenerationTokensRef = new AtomicReference<>(0);
		AtomicReference<Integer> metadataUsageTotalTokensRef = new AtomicReference<>(0);

		AtomicReference<PromptMetadata> metadataPromptMetadataRef = new AtomicReference<>(PromptMetadata.empty());
		AtomicReference<RateLimit> metadataRateLimitRef = new AtomicReference<>(new EmptyRateLimit());
		// Usage details reported by the provider on the chunk that carries real usage:
		// the native usage object (OpenAI CompletionUsage, Anthropic Usage, ...) and the
		// prompt-cache metrics. They are reused for the aggregated response so that it
		// does not collapse to plain token counts (gh-6996).
		AtomicReference<@Nullable Object> metadataNativeUsageRef = new AtomicReference<>();
		AtomicReference<@Nullable Long> metadataCacheReadTokensRef = new AtomicReference<>();
		AtomicReference<@Nullable Long> metadataCacheWriteTokensRef = new AtomicReference<>();

		AtomicReference<String> metadataIdRef = new AtomicReference<>("");
		AtomicReference<String> metadataModelRef = new AtomicReference<>("");

		return fluxChatResponse.doOnSubscribe(subscription -> {
			messageTextContentRef.set(new StringBuilder());
			thoughtsRef.set(new StringBuilder());
			outputWithoutThoughtsRef.set(new StringBuilder());
			messageMetadataMapRef.set(new HashMap<>());
			toolCallsRef.set(new ArrayList<>());
			carriedPartsRef.set(new ArrayList<>());
			indexedGroupsRef.set(new ArrayList<>());
			metadataIdRef.set("");
			metadataModelRef.set("");
			metadataUsagePromptTokensRef.set(0);
			metadataUsageGenerationTokensRef.set(0);
			metadataUsageTotalTokensRef.set(0);
			metadataPromptMetadataRef.set(PromptMetadata.empty());
			metadataRateLimitRef.set(new EmptyRateLimit());
			metadataNativeUsageRef.set(null);
			metadataCacheReadTokensRef.set(null);
			metadataCacheWriteTokensRef.set(null);

		}).doOnNext(chatResponse -> {

			if (chatResponse.getResult() != null) {
				if (chatResponse.getResult().getMetadata() != null
						&& chatResponse.getResult().getMetadata() != ChatGenerationMetadata.NULL) {
					generationMetadataRef.set(chatResponse.getResult().getMetadata());
				}
				AssistantMessage outputMessage = chatResponse.getResult().getOutput();
				Map<String, Object> outputMetadata = outputMessage.getMetadata();
				if (outputMetadata != null) {
					messageMetadataMapRef.get().putAll(outputMetadata);
				}
				Boolean isThought = (outputMetadata != null && outputMetadata.containsKey("isThought"))
						? Boolean.parseBoolean(String.valueOf(outputMetadata.get("isThought"))) : null;
				String responseId = (chatResponse.getMetadata() != null && chatResponse.getMetadata().getId() != null)
						? chatResponse.getMetadata().getId() : "";
				for (MessagePart part : outputMessage.getParts()) {
					Integer index = StreamingParts.partIndex(part);
					if (index != null) {
						groupFor(indexedGroupsRef.get(), responseId, index, part).accept(index, part);
					}
					else if (part instanceof TextPart textPart) {
						messageTextContentRef.get().append(textPart.text());
						if (isThought != null) {
							(isThought ? thoughtsRef : outputWithoutThoughtsRef).get().append(textPart.text());
						}
					}
					else if (part instanceof ToolCallPart toolCallPart) {
						toolCallsRef.get().add(toolCallPart.toolCall());
					}
					else if (!(part instanceof MediaPart)) {
						carriedPartsRef.get().add(part);
					}

				}

			}
			if (chatResponse.getMetadata() != null) {
				if (chatResponse.getMetadata().getUsage() != null) {
					Usage usage = chatResponse.getMetadata().getUsage();
					// Empty usages carry no tokens and no provider information, so they
					// must
					// not clobber what a usage with real data reported on another chunk.
					if (reportedTokens(usage)) {
						metadataUsagePromptTokensRef.set(usage.getPromptTokens() > 0 ? usage.getPromptTokens()
								: metadataUsagePromptTokensRef.get());
						metadataUsageGenerationTokensRef.set(usage.getCompletionTokens() > 0
								? usage.getCompletionTokens() : metadataUsageGenerationTokensRef.get());
						metadataUsageTotalTokensRef.set(usage.getTotalTokens() > 0 ? usage.getTotalTokens()
								: metadataUsageTotalTokensRef.get());
						// Keep the provider details for the aggregated response
						// (gh-6996).
						if (usage.getNativeUsage() != null) {
							metadataNativeUsageRef.set(usage.getNativeUsage());
						}
						if (usage.getCacheReadInputTokens() != null) {
							metadataCacheReadTokensRef.set(usage.getCacheReadInputTokens());
						}
						if (usage.getCacheWriteInputTokens() != null) {
							metadataCacheWriteTokensRef.set(usage.getCacheWriteInputTokens());
						}
					}
				}
				if (chatResponse.getMetadata().getPromptMetadata() != null
						&& chatResponse.getMetadata().getPromptMetadata().iterator().hasNext()) {
					metadataPromptMetadataRef.set(chatResponse.getMetadata().getPromptMetadata());
				}
				RateLimit incomingRateLimit = chatResponse.getMetadata().getRateLimit();
				if (incomingRateLimit != null && !(incomingRateLimit instanceof EmptyRateLimit)) {
					metadataRateLimitRef.set(incomingRateLimit);
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

			Object nativeUsage = metadataNativeUsageRef.get();
			Long cacheReadInputTokens = metadataCacheReadTokensRef.get();
			Long cacheWriteInputTokens = metadataCacheWriteTokensRef.get();
			// Keep the provider-native usage object (for example OpenAI
			// CompletionUsage) and the prompt-cache metrics reported by the model, so
			// that the aggregated response does not lose them. When nothing beyond the
			// token counts is available, fall back to the previous token-only usage.
			Usage usage;
			if (nativeUsage != null || cacheReadInputTokens != null || cacheWriteInputTokens != null) {
				usage = new org.springframework.ai.chat.metadata.DefaultUsage(metadataUsagePromptTokensRef.get(),
						metadataUsageGenerationTokensRef.get(), metadataUsageTotalTokensRef.get(), nativeUsage,
						cacheReadInputTokens, cacheWriteInputTokens);
			}
			else {
				usage = new DefaultUsage(metadataUsagePromptTokensRef.get(), metadataUsageGenerationTokensRef.get(),
						metadataUsageTotalTokensRef.get());
			}

			var chatResponseMetadata = ChatResponseMetadata.builder()
				.id(metadataIdRef.get())
				.model(metadataModelRef.get())
				.rateLimit(metadataRateLimitRef.get())
				.usage(usage)
				.promptMetadata(metadataPromptMetadataRef.get())
				.build();

			// Final part order: indexed groups (by index within a group), then unindexed
			// carried parts, then the legacy text and tool calls, built explicitly so
			// that no builder ordering policy is relied on.
			List<MessagePart> parts = new ArrayList<>();
			boolean indexed = false;
			for (IndexedGroup group : indexedGroupsRef.get()) {
				for (IndexedSlot slot : group.slots.values()) {
					parts.add(StreamingParts.strip(slot.toPart()));
					indexed = true;
				}
			}
			parts.addAll(carriedPartsRef.get());
			String legacyText = messageTextContentRef.get().toString();
			if (!indexed || !legacyText.isEmpty()) {
				// Legacy streams always end with a text part, possibly empty, as before.
				parts.add(TextPart.of(legacyText));
			}
			for (ToolCall toolCall : toolCallsRef.get()) {
				parts.add(ToolCallPart.of(toolCall));
			}

			var messageMetadata = messageMetadataMapRef.get();
			if (!thoughtsRef.get().isEmpty()) {
				// Deprecated: kept for one release for chat models that still flag
				// unindexed text chunks with "isThought". Thought text of indexed streams
				// is a ReasoningPart.
				messageMetadata.put("thoughts", thoughtsRef.get().toString());
				messageMetadata.put("outputWithoutThoughts", outputWithoutThoughtsRef.get().toString());
			}
			AssistantMessage finalAssistantMessage = AssistantMessage.builder()
				.parts(parts)
				.properties(messageMetadata)
				.build();

			onAggregationComplete.accept(new ChatResponse(List.of(new Generation(finalAssistantMessage,

					generationMetadataRef.get())), chatResponseMetadata));

			messageTextContentRef.set(new StringBuilder());
			thoughtsRef.set(new StringBuilder());
			outputWithoutThoughtsRef.set(new StringBuilder());
			messageMetadataMapRef.set(new HashMap<>());
			toolCallsRef.set(new ArrayList<>());
			carriedPartsRef.set(new ArrayList<>());
			indexedGroupsRef.set(new ArrayList<>());
			metadataIdRef.set("");
			metadataModelRef.set("");
			metadataUsagePromptTokensRef.set(0);
			metadataUsageGenerationTokensRef.set(0);
			metadataUsageTotalTokensRef.set(0);
			metadataPromptMetadataRef.set(PromptMetadata.empty());
			metadataRateLimitRef.set(new EmptyRateLimit());
			metadataNativeUsageRef.set(null);
			metadataCacheReadTokensRef.set(null);
			metadataCacheWriteTokensRef.set(null);

		}).doOnError(e -> logger.error("Aggregation Error", e));
	}

	/**
	 * Finds or creates the group an indexed part belongs to. With a response id, the
	 * group is the one with that id. Without one, the current group is continued unless
	 * the index goes backwards or the slot at that index already holds content the part
	 * cannot continue, both of which mark the start of a new response in the stream.
	 */
	private static IndexedGroup groupFor(List<IndexedGroup> groups, String responseId, int index, MessagePart part) {
		if (StringUtils.hasText(responseId)) {
			for (IndexedGroup group : groups) {
				if (responseId.equals(group.id)) {
					return group;
				}
			}
			return newGroup(groups, responseId);
		}
		if (groups.isEmpty()) {
			return newGroup(groups, "");
		}
		IndexedGroup last = groups.get(groups.size() - 1);
		if (!last.id.isEmpty() || index < last.maxIndex) {
			return newGroup(groups, "");
		}
		IndexedSlot slot = last.slots.get(index);
		if (slot != null && !slot.canContinueWith(part)) {
			return newGroup(groups, "");
		}
		return last;
	}

	private static IndexedGroup newGroup(List<IndexedGroup> groups, String id) {
		IndexedGroup group = new IndexedGroup(id);
		groups.add(group);
		return group;
	}

	/**
	 * Whether the given usage reports at least one non-zero token count, meaning it was
	 * actually populated by the provider.
	 * @param usage the usage to inspect
	 * @return {@code true} when the usage carries token information
	 */
	private static boolean reportedTokens(Usage usage) {
		return usage.getPromptTokens() > 0 || usage.getCompletionTokens() > 0 || usage.getTotalTokens() > 0;
	}

	/**
	 * Default implementation of {@link Usage} for aggregated streaming responses.
	 *
	 * @param promptTokens the prompt tokens
	 * @param completionTokens the completion tokens
	 * @param totalTokens the total tokens
	 */
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

	/**
	 * The indexed parts of one streamed response.
	 */
	private static final class IndexedGroup {

		private final String id;

		private final TreeMap<Integer, IndexedSlot> slots = new TreeMap<>();

		private int maxIndex = -1;

		IndexedGroup(String id) {
			this.id = id;
		}

		void accept(int index, MessagePart part) {
			this.slots.computeIfAbsent(index, key -> new IndexedSlot()).accept(part);
			this.maxIndex = Math.max(this.maxIndex, index);
		}

	}

	/**
	 * Accumulates the deltas of one content block without rebuilding the whole part per
	 * delta. Text, summary and tool call arguments are appended in builders; the part is
	 * materialized once in {@link #toPart()}.
	 */
	private static final class IndexedSlot {

		private @Nullable MessagePart part;

		private final StringBuilder text = new StringBuilder();

		private boolean textPresent;

		private final StringBuilder summary = new StringBuilder();

		private boolean summaryPresent;

		private @Nullable OpaquePayload payload;

		private final StringBuilder arguments = new StringBuilder();

		private String toolCallId = "";

		private String toolCallType = "";

		private String toolCallName = "";

		private boolean complete;

		boolean canContinueWith(MessagePart incoming) {
			return this.part != null && !this.complete && this.part.getClass() == incoming.getClass();
		}

		void accept(MessagePart incoming) {
			boolean partial = StreamingParts.isPartial(incoming);
			if (this.part == null || !partial || this.part.getClass() != incoming.getClass()) {
				reset(incoming, !partial);
				return;
			}
			this.part = incoming;
			if (incoming instanceof TextPart delta) {
				this.text.append(delta.text());
				this.textPresent = true;
			}
			else if (incoming instanceof ReasoningPart delta) {
				if (delta.text() != null) {
					this.text.append(delta.text());
					this.textPresent = true;
				}
				if (delta.summary() != null) {
					this.summary.append(delta.summary());
					this.summaryPresent = true;
				}
			}
			else if (incoming instanceof ToolCallPart delta) {
				ToolCall toolCall = delta.toolCall();
				if (StringUtils.hasText(toolCall.id())) {
					this.toolCallId = toolCall.id();
				}
				if (StringUtils.hasText(toolCall.type())) {
					this.toolCallType = toolCall.type();
				}
				if (StringUtils.hasText(toolCall.name())) {
					this.toolCallName = toolCall.name();
				}
				String next = toolCall.arguments();
				if (next != null) {
					this.arguments.append(next);
				}
			}
			if (incoming.payload() != null) {
				this.payload = incoming.payload();
			}
		}

		private void reset(MessagePart incoming, boolean complete) {
			this.part = incoming;
			this.complete = complete;
			this.payload = incoming.payload();
			this.text.setLength(0);
			this.summary.setLength(0);
			this.arguments.setLength(0);
			this.textPresent = false;
			this.summaryPresent = false;
			if (incoming instanceof TextPart delta) {
				this.text.append(delta.text());
				this.textPresent = true;
			}
			else if (incoming instanceof ReasoningPart delta) {
				if (delta.text() != null) {
					this.text.append(delta.text());
					this.textPresent = true;
				}
				if (delta.summary() != null) {
					this.summary.append(delta.summary());
					this.summaryPresent = true;
				}
			}
			else if (incoming instanceof ToolCallPart delta) {
				ToolCall toolCall = delta.toolCall();
				this.toolCallId = toolCall.id();
				this.toolCallType = toolCall.type();
				this.toolCallName = toolCall.name();
				String args = toolCall.arguments();
				if (args != null) {
					this.arguments.append(args);
				}
			}
		}

		MessagePart toPart() {
			MessagePart last = this.part;
			if (last == null) {
				throw new IllegalStateException("Slot has no part");
			}
			if (last instanceof TextPart) {
				return new TextPart(this.text.toString(), this.payload, last.attributes());
			}
			if (last instanceof ReasoningPart current) {
				return new ReasoningPart(this.textPresent ? this.text.toString() : null,
						this.summaryPresent ? this.summary.toString() : null, this.payload, current.attributes());
			}
			if (last instanceof ToolCallPart current) {
				return new ToolCallPart(
						new ToolCall(this.toolCallId, this.toolCallType, this.toolCallName, this.arguments.toString()),
						this.payload, current.attributes());
			}
			return last;
		}

	}

}
