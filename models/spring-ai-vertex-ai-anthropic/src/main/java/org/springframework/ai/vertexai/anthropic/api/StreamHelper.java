/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.vertexai.anthropic.api;

import org.springframework.ai.vertexai.anthropic.model.ApiUsage;
import org.springframework.ai.vertexai.anthropic.model.ChatCompletionResponse;
import org.springframework.ai.vertexai.anthropic.model.ContentBlock;
import org.springframework.ai.vertexai.anthropic.model.Role;
import org.springframework.ai.vertexai.anthropic.model.stream.*;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Helper class to support streaming function calling.
 * <p>
 * It can merge the streamed {@link StreamEvent} chunks in case of function calling
 * message.
 *
 * @author Alessio Bertazzo
 * @since 1.0.0
 */
public class StreamHelper {

	/**
	 * Checks if the given event is a tool use start event.
	 * @param event the StreamEvent to check
	 * @return true if the event is a tool use start event, false otherwise
	 */
	public boolean isToolUseStart(StreamEvent event) {
		if (event == null || event.type() == null || event.type() != EventType.CONTENT_BLOCK_START) {
			return false;
		}
		return ContentBlock.Type.TOOL_USE.getValue().equals(((ContentBlockStartEvent) event).contentBlock().type());
	}

	/**
	 * Checks if the given event is a tool use start event.
	 * @param event the StreamEvent to check
	 * @return true if the event is a tool use start event, false otherwise
	 */
	public boolean isToolUseFinish(StreamEvent event) {

		if (event == null || event.type() == null || event.type() != EventType.CONTENT_BLOCK_STOP) {
			return false;
		}
		return true;
	}

	/**
	 * Merges tool use events into a single aggregated event.
	 * @param previousEvent the previous StreamEvent, expected to be a
	 * ToolUseAggregationEvent
	 * @param event the current StreamEvent to merge
	 * @return the merged StreamEvent, or the original event if no merging is performed
	 */
	public StreamEvent mergeToolUseEvents(StreamEvent previousEvent, StreamEvent event) {

		ToolUseAggregationEvent eventAggregator = (ToolUseAggregationEvent) previousEvent;

		if (event.type() == EventType.CONTENT_BLOCK_START) {
			ContentBlockStartEvent contentBlockStart = (ContentBlockStartEvent) event;

			if (ContentBlock.Type.TOOL_USE.getValue().equals(contentBlockStart.contentBlock().type())) {
				ContentBlockStartEvent.ContentBlockToolUse cbToolUse = (ContentBlockStartEvent.ContentBlockToolUse) contentBlockStart
					.contentBlock();

				return eventAggregator.withIndex(contentBlockStart.index())
					.withId(cbToolUse.id())
					.withName(cbToolUse.name())
					.appendPartialJson("");
			}
		}
		else if (event.type() == EventType.CONTENT_BLOCK_DELTA) {
			ContentBlockDeltaEvent contentBolckDelta = (ContentBlockDeltaEvent) event;
			if (ContentBlock.Type.INPUT_JSON_DELTA.getValue().equals(contentBolckDelta.delta().type())) {
				return eventAggregator.appendPartialJson(
						((ContentBlockDeltaEvent.ContentBlockDeltaJson) contentBolckDelta.delta()).partialJson());
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
	 * Converts a StreamEvent to a ChatCompletionResponse.
	 * @param event the StreamEvent to convert
	 * @param contentBlockReference a reference to the ChatCompletionResponseBuilder
	 * @return the constructed ChatCompletionResponse
	 */
	public ChatCompletionResponse eventToChatCompletionResponse(StreamEvent event,
			AtomicReference<ChatCompletionResponseBuilder> contentBlockReference) {

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
					.map(tooToUse -> new ContentBlock(ContentBlock.Type.TOOL_USE, tooToUse.id(), tooToUse.name(),
							tooToUse.input()))
					.toList();
				contentBlockReference.get().withContent(content);
			}
		}
		else if (event.type().equals(EventType.CONTENT_BLOCK_START)) {
			ContentBlockStartEvent contentBlockStartEvent = (ContentBlockStartEvent) event;

			Assert.isTrue(contentBlockStartEvent.contentBlock().type().equals("text"),
					"The json content block should have been aggregated. Unsupported content block type: "
							+ contentBlockStartEvent.contentBlock().type());

			ContentBlockStartEvent.ContentBlockText contentBlockText = (ContentBlockStartEvent.ContentBlockText) contentBlockStartEvent
				.contentBlock();
			ContentBlock contentBlock = new ContentBlock(ContentBlock.Type.TEXT, null, contentBlockText.text(),
					contentBlockStartEvent.index());
			contentBlockReference.get().withType(event.type().name()).withContent(List.of(contentBlock));
		}
		else if (event.type().equals(EventType.CONTENT_BLOCK_DELTA)) {

			ContentBlockDeltaEvent contentBlockDeltaEvent = (ContentBlockDeltaEvent) event;

			Assert.isTrue(contentBlockDeltaEvent.delta().type().equals("text_delta"),
					"The json content block delta should have been aggregated. Unsupported content block type: "
							+ contentBlockDeltaEvent.delta().type());

			ContentBlockDeltaEvent.ContentBlockDeltaText deltaTxt = (ContentBlockDeltaEvent.ContentBlockDeltaText) contentBlockDeltaEvent
				.delta();

			var contentBlock = new ContentBlock(ContentBlock.Type.TEXT_DELTA, null, deltaTxt.text(),
					contentBlockDeltaEvent.index());

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
				var totalUsage = new ApiUsage(contentBlockReference.get().usage.inputTokens(),
						messageDeltaEvent.usage().outputTokens());
				contentBlockReference.get().withUsage(totalUsage);
			}
		}
		else if (event.type().equals(EventType.MESSAGE_STOP)) {
		}
		else {
			contentBlockReference.get().withType(event.type().name()).withContent(List.of());
		}

		return contentBlockReference.get().build();
	}

	/**
	 * Builder class for constructing ChatCompletionResponse objects.
	 */
	public static class ChatCompletionResponseBuilder {

		private String type;

		private String id;

		private Role role;

		private List<ContentBlock> content;

		private String model;

		private String stopReason;

		private String stopSequence;

		private ApiUsage usage;

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

		public ChatCompletionResponseBuilder withUsage(ApiUsage usage) {
			this.usage = usage;
			return this;
		}

		public ChatCompletionResponse build() {
			return new ChatCompletionResponse(this.id, this.type, this.role, this.content, this.model, this.stopReason,
					this.stopSequence, this.usage);
		}

	}

}
