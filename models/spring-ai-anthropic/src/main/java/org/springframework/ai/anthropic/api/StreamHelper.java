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

package org.springframework.ai.anthropic.api;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionResponse;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock.Type;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlockDeltaEvent;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlockDeltaEvent.ContentBlockDeltaJson;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlockDeltaEvent.ContentBlockDeltaText;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlockStartEvent;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlockStartEvent.ContentBlockText;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlockStartEvent.ContentBlockToolUse;
import org.springframework.ai.anthropic.api.AnthropicApi.EventType;
import org.springframework.ai.anthropic.api.AnthropicApi.MessageDeltaEvent;
import org.springframework.ai.anthropic.api.AnthropicApi.MessageStartEvent;
import org.springframework.ai.anthropic.api.AnthropicApi.Role;
import org.springframework.ai.anthropic.api.AnthropicApi.StreamEvent;
import org.springframework.ai.anthropic.api.AnthropicApi.ToolUseAggregationEvent;
import org.springframework.ai.anthropic.api.AnthropicApi.Usage;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Helper class to support streaming function calling.
 * <p>
 * It can merge the streamed {@link StreamEvent} chunks in case of function calling
 * message.
 *
 * @author Mariusz Bernacki
 * @author Christian Tzolov
 * @author Jihoon Kim
 * @since 1.0.0
 */
public class StreamHelper {

	public boolean isToolUseStart(StreamEvent event) {
		if (event == null || event.type() == null || event.type() != EventType.CONTENT_BLOCK_START) {
			return false;
		}
		return ContentBlock.Type.TOOL_USE.getValue().equals(((ContentBlockStartEvent) event).contentBlock().type());
	}

	public boolean isToolUseFinish(StreamEvent event) {

		if (event == null || event.type() == null || event.type() != EventType.CONTENT_BLOCK_STOP) {
			return false;
		}
		return true;
	}

	public StreamEvent mergeToolUseEvents(StreamEvent previousEvent, StreamEvent event) {

		ToolUseAggregationEvent eventAggregator = (ToolUseAggregationEvent) previousEvent;

		if (event.type() == EventType.CONTENT_BLOCK_START) {
			ContentBlockStartEvent contentBlockStart = (ContentBlockStartEvent) event;

			if (ContentBlock.Type.TOOL_USE.getValue().equals(contentBlockStart.contentBlock().type())) {
				ContentBlockStartEvent.ContentBlockToolUse cbToolUse = (ContentBlockToolUse) contentBlockStart
					.contentBlock();

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

			Assert.isTrue(contentBlockStartEvent.contentBlock().type().equals("text"),
					"The json content block should have been aggregated. Unsupported content block type: "
							+ contentBlockStartEvent.contentBlock().type());

			ContentBlockText contentBlockText = (ContentBlockText) contentBlockStartEvent.contentBlock();
			ContentBlock contentBlock = new ContentBlock(Type.TEXT, null, contentBlockText.text(),
					contentBlockStartEvent.index());
			contentBlockReference.get().withType(event.type().name()).withContent(List.of(contentBlock));
		}
		else if (event.type().equals(EventType.CONTENT_BLOCK_DELTA)) {

			ContentBlockDeltaEvent contentBlockDeltaEvent = (ContentBlockDeltaEvent) event;

			Assert.isTrue(contentBlockDeltaEvent.delta().type().equals("text_delta"),
					"The json content block delta should have been aggregated. Unsupported content block type: "
							+ contentBlockDeltaEvent.delta().type());

			ContentBlockDeltaText deltaTxt = (ContentBlockDeltaText) contentBlockDeltaEvent.delta();

			var contentBlock = new ContentBlock(Type.TEXT_DELTA, null, deltaTxt.text(), contentBlockDeltaEvent.index());

			contentBlockReference.get().withType(event.type().name()).withContent(List.of(contentBlock));
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
				var totalUsage = new Usage(contentBlockReference.get().usage.inputTokens(),
						messageDeltaEvent.usage().outputTokens());
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
			contentBlockReference.get().withType(event.type().name()).withContent(List.of());
		}

		return contentBlockReference.get().build();
	}

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
					this.stopSequence, this.usage);
		}

	}

}
