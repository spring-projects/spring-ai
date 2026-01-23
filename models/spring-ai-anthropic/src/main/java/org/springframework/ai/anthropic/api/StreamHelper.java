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

package org.springframework.ai.anthropic.api;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionResponse;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock.Type;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlockDeltaEvent;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlockDeltaEvent.ContentBlockDeltaJson;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlockDeltaEvent.ContentBlockDeltaSignature;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlockDeltaEvent.ContentBlockDeltaText;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlockDeltaEvent.ContentBlockDeltaThinking;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlockStartEvent;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlockStartEvent.ContentBlockText;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlockStartEvent.ContentBlockThinking;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlockStartEvent.ContentBlockToolUse;
import org.springframework.ai.anthropic.api.AnthropicApi.EventType;
import org.springframework.ai.anthropic.api.AnthropicApi.MessageDeltaEvent;
import org.springframework.ai.anthropic.api.AnthropicApi.MessageStartEvent;
import org.springframework.ai.anthropic.api.AnthropicApi.Role;
import org.springframework.ai.anthropic.api.AnthropicApi.StreamEvent;
import org.springframework.ai.anthropic.api.AnthropicApi.ToolUseAggregationEvent;
import org.springframework.ai.anthropic.api.AnthropicApi.Usage;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Helper class to support streaming function calling and thinking events.
 * <p>
 * It can merge the streamed {@link StreamEvent} chunks in case of function calling
 * message. It passes through other events like text, thinking, and signature deltas.
 *
 * @author Mariusz Bernacki
 * @author Christian Tzolov
 * @author Jihoon Kim
 * @author Alexandros Pappas
 * @author Claudio Silva Junior
 * @author Soby Chacko
 * @author Sun Yuhan
 * @since 1.0.0
 */
public class StreamHelper {

	private static final Logger logger = LoggerFactory.getLogger(StreamHelper.class);

	public boolean isToolUseStart(StreamEvent event) {
		if (event == null || event.type() == null || event.type() != EventType.CONTENT_BLOCK_START) {
			return false;
		}
		return ContentBlock.Type.TOOL_USE.getValue().equals(((ContentBlockStartEvent) event).contentBlock().type());
	}

	public boolean isToolUseFinish(StreamEvent event) {
		// Tool use streaming sequence ends with a CONTENT_BLOCK_STOP event.
		// The logic relies on the state machine (isInsideTool flag) managed in
		// chatCompletionStream to know if this stop event corresponds to a tool use.
		return event != null && event.type() != null && event.type() == EventType.CONTENT_BLOCK_STOP;
	}

	/**
	 * Merge the tool‑use related streaming events into one aggregate event so that the
	 * upper layers see a single ContentBlock with the full JSON input.
	 */
	public StreamEvent mergeToolUseEvents(StreamEvent previousEvent, StreamEvent event) {

		if (!(previousEvent instanceof ToolUseAggregationEvent eventAggregator)) {
			return event;
		}

		if (event.type() == EventType.CONTENT_BLOCK_START) {
			ContentBlockStartEvent contentBlockStart = (ContentBlockStartEvent) event;

			if (ContentBlock.Type.TOOL_USE.getValue().equals(contentBlockStart.contentBlock().type())) {
				ContentBlockToolUse cbToolUse = (ContentBlockToolUse) contentBlockStart.contentBlock();

				return eventAggregator.withIndex(contentBlockStart.index())
					.withId(cbToolUse.id())
					.withName(cbToolUse.name())
					.appendPartialJson(""); // CB START always has empty JSON.
			}
		}
		else if (event.type() == EventType.CONTENT_BLOCK_DELTA) {
			ContentBlockDeltaEvent contentBlockDelta = (ContentBlockDeltaEvent) event;
			if (ContentBlock.Type.INPUT_JSON_DELTA.getValue().equals(contentBlockDelta.delta().type())) {
				return eventAggregator
					.appendPartialJson(((ContentBlockDeltaJson) contentBlockDelta.delta()).partialJson());
			}
		}
		else if (event.type() == EventType.CONTENT_BLOCK_STOP) {
			if (!eventAggregator.isEmpty()) {
				eventAggregator.squashIntoContentBlock();
				return eventAggregator;
			}
		}

		return event;
	}

	/**
	 * Converts a raw {@link StreamEvent} potentially containing tool use aggregates or
	 * other block types (text, thinking) into a {@link ChatCompletionResponse} chunk.
	 * @param event The incoming StreamEvent.
	 * @param contentBlockReference Holds the state of the response being built across
	 * multiple events.
	 * @return A ChatCompletionResponse representing the processed chunk.
	 */
	public ChatCompletionResponse eventToChatCompletionResponse(StreamEvent event,
			AtomicReference<ChatCompletionResponseBuilder> contentBlockReference) {

		// https://docs.anthropic.com/claude/reference/messages-streaming

		if (event.type().equals(EventType.MESSAGE_START)) {
			contentBlockReference.set(new ChatCompletionResponseBuilder());

			MessageStartEvent messageStartEvent = (MessageStartEvent) event;

			contentBlockReference.get()
				.withType(event.type().name())
				.withId(messageStartEvent.message().id())
				.withRole(messageStartEvent.message().role())
				.withModel(messageStartEvent.message().model())
				.withUsage(messageStartEvent.message().usage())
				.withContent(new ArrayList<>());
		}
		else if (event.type().equals(EventType.TOOL_USE_AGGREGATE)) {
			ToolUseAggregationEvent eventToolUseBuilder = (ToolUseAggregationEvent) event;

			if (!CollectionUtils.isEmpty(eventToolUseBuilder.getToolContentBlocks())) {

				List<ContentBlock> content = eventToolUseBuilder.getToolContentBlocks()
					.stream()
					.map(tooToUse -> new ContentBlock(Type.TOOL_USE, tooToUse.id(), tooToUse.name(), tooToUse.input()))
					.toList();
				contentBlockReference.get().withContent(content);
			}
		}
		else if (event.type().equals(EventType.CONTENT_BLOCK_START)) {
			ContentBlockStartEvent contentBlockStartEvent = (ContentBlockStartEvent) event;

			if (contentBlockStartEvent.contentBlock() instanceof ContentBlockText textBlock) {
				ContentBlock cb = new ContentBlock(Type.TEXT, null, textBlock.text(), contentBlockStartEvent.index());
				contentBlockReference.get().withType(event.type().name()).withContent(List.of(cb));
			}
			else if (contentBlockStartEvent.contentBlock() instanceof ContentBlockThinking thinkingBlock) {
				ContentBlock cb = new ContentBlock(Type.THINKING, null, null, contentBlockStartEvent.index(), null,
						null, null, null, null, thinkingBlock.signature(), thinkingBlock.thinking(), null, null, null,
						null, null, null, null);
				contentBlockReference.get().withType(event.type().name()).withContent(List.of(cb));
			}
			else {
				throw new IllegalArgumentException(
						"Unsupported content block type: " + contentBlockStartEvent.contentBlock().type());
			}
		}
		else if (event.type().equals(EventType.CONTENT_BLOCK_DELTA)) {
			ContentBlockDeltaEvent contentBlockDeltaEvent = (ContentBlockDeltaEvent) event;

			if (contentBlockDeltaEvent.delta() instanceof ContentBlockDeltaText txt) {
				ContentBlock cb = new ContentBlock(Type.TEXT_DELTA, null, txt.text(), contentBlockDeltaEvent.index());
				contentBlockReference.get().withType(event.type().name()).withContent(List.of(cb));
			}
			else if (contentBlockDeltaEvent.delta() instanceof ContentBlockDeltaThinking thinking) {
				ContentBlock cb = new ContentBlock(Type.THINKING_DELTA, null, null, contentBlockDeltaEvent.index(),
						null, null, null, null, null, null, thinking.thinking(), null, null, null, null, null, null,
						null);
				contentBlockReference.get().withType(event.type().name()).withContent(List.of(cb));
			}
			else if (contentBlockDeltaEvent.delta() instanceof ContentBlockDeltaSignature sig) {
				ContentBlock cb = new ContentBlock(Type.SIGNATURE_DELTA, null, null, contentBlockDeltaEvent.index(),
						null, null, null, null, null, sig.signature(), null, null, null, null, null, null, null, null);
				contentBlockReference.get().withType(event.type().name()).withContent(List.of(cb));
			}
			else {
				throw new IllegalArgumentException(
						"Unsupported content block delta type: " + contentBlockDeltaEvent.delta().type());
			}
		}
		else if (event.type().equals(EventType.MESSAGE_DELTA)) {

			contentBlockReference.get().withType(event.type().name());

			MessageDeltaEvent messageDeltaEvent = (MessageDeltaEvent) event;

			if (StringUtils.hasText(messageDeltaEvent.delta().stopReason())) {
				contentBlockReference.get().withStopReason(messageDeltaEvent.delta().stopReason());
			}

			if (StringUtils.hasText(messageDeltaEvent.delta().stopSequence())) {
				contentBlockReference.get().withStopSequence(messageDeltaEvent.delta().stopSequence());
			}

			if (messageDeltaEvent.usage() != null) {
				Usage totalUsage = new Usage(contentBlockReference.get().usage.inputTokens(),
						messageDeltaEvent.usage().outputTokens(),
						contentBlockReference.get().usage.cacheCreationInputTokens(),
						contentBlockReference.get().usage.cacheReadInputTokens());
				contentBlockReference.get().withUsage(totalUsage);
			}
		}
		else if (event.type().equals(EventType.MESSAGE_STOP)) {
			// Don't return the latest Content block as it was before. Instead, return it
			// with an updated event type and general information like: model, message
			// type, id and usage
			contentBlockReference.get()
				.withType(event.type().name())
				.withContent(List.of())
				.withStopReason(null)
				.withStopSequence(null);
		}
		else {
			// Any other event types that should propagate upwards without content
			if (contentBlockReference.get() == null) {
				contentBlockReference.set(new ChatCompletionResponseBuilder());
			}
			contentBlockReference.get().withType(event.type().name()).withContent(List.of());
			logger.warn("Unhandled event type: {}", event.type().name());
		}

		return contentBlockReference.get().build();
	}

	/**
	 * Builder for {@link ChatCompletionResponse}. Used internally by {@link StreamHelper}
	 * to aggregate stream events.
	 */
	public static class ChatCompletionResponseBuilder {

		private String type;

		private String id;

		private Role role;

		private List<ContentBlock> content;

		private String model;

		private String stopReason;

		private String stopSequence;

		private Usage usage;

		public ChatCompletionResponseBuilder() {
		}

		public ChatCompletionResponseBuilder withType(String type) {
			this.type = type;
			return this;
		}

		public ChatCompletionResponseBuilder withId(String id) {
			this.id = id;
			return this;
		}

		public ChatCompletionResponseBuilder withRole(Role role) {
			this.role = role;
			return this;
		}

		public ChatCompletionResponseBuilder withContent(List<ContentBlock> content) {
			this.content = content;
			return this;
		}

		public ChatCompletionResponseBuilder withModel(String model) {
			this.model = model;
			return this;
		}

		public ChatCompletionResponseBuilder withStopReason(String stopReason) {
			this.stopReason = stopReason;
			return this;
		}

		public ChatCompletionResponseBuilder withStopSequence(String stopSequence) {
			this.stopSequence = stopSequence;
			return this;
		}

		public ChatCompletionResponseBuilder withUsage(Usage usage) {
			this.usage = usage;
			return this;
		}

		public ChatCompletionResponse build() {
			return new ChatCompletionResponse(this.id, this.type, this.role, this.content, this.model, this.stopReason,
					this.stopSequence, this.usage, null);
		}

	}

}
