/*
 * Copyright 2025-2025 the original author or authors.
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

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionResponse;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlockDeltaEvent;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlockStartEvent;
import org.springframework.ai.anthropic.api.AnthropicApi.MessageDeltaEvent;
import org.springframework.ai.anthropic.api.AnthropicApi.MessageStartEvent;
import org.springframework.ai.anthropic.api.AnthropicApi.MessageStopEvent;
import org.springframework.ai.anthropic.api.AnthropicApi.PingEvent;
import org.springframework.ai.anthropic.api.AnthropicApi.Role;
import org.springframework.ai.anthropic.api.AnthropicApi.Usage;
import org.springframework.ai.anthropic.api.StreamHelper.ChatCompletionResponseBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

/**
 * Tests for {@link StreamHelper}.
 *
 * @author Ilayaperumal Gopinathan
 * @author Sun Yuhan
 */
class StreamHelperTests {

	@Test
	void testErrorEventTypeWithEmptyContentBlock() {
		AtomicReference<ChatCompletionResponseBuilder> contentBlockReference = new AtomicReference<>();
		StreamHelper streamHelper = new StreamHelper();

		// Initialize content block reference with a message start event. This ensures
		// that message id, model and content are set in the contentBlockReference
		ChatCompletionResponse message = new ChatCompletionResponse("msg-1", "message", Role.ASSISTANT, List.of(),
				"claude-haiku-4-5", null, null, null, null);
		streamHelper.eventToChatCompletionResponse(new MessageStartEvent(AnthropicApi.EventType.MESSAGE_START, message),
				contentBlockReference);

		AnthropicApi.ErrorEvent errorEvent = new AnthropicApi.ErrorEvent(AnthropicApi.EventType.ERROR,
				new AnthropicApi.ErrorEvent.Error("error", "error message"));

		AnthropicApi.ChatCompletionResponse response = streamHelper.eventToChatCompletionResponse(errorEvent,
				contentBlockReference);
		assertThat(response).isNotNull();
	}

	@Test
	void testMultipleErrorEventsHandling() {
		StreamHelper streamHelper = new StreamHelper();
		AtomicReference<ChatCompletionResponseBuilder> contentBlockReference = new AtomicReference<>();

		// Initialize content block reference with a message start event. This ensures
		// that message id, model and content are set in the contentBlockReference
		ChatCompletionResponse message = new ChatCompletionResponse("msg-1", "message", Role.ASSISTANT, List.of(),
				"claude-haiku-4-5", null, null, null, null);
		streamHelper.eventToChatCompletionResponse(new MessageStartEvent(AnthropicApi.EventType.MESSAGE_START, message),
				contentBlockReference);

		AnthropicApi.ErrorEvent firstError = new AnthropicApi.ErrorEvent(AnthropicApi.EventType.ERROR,
				new AnthropicApi.ErrorEvent.Error("validation_error", "Invalid input"));
		AnthropicApi.ErrorEvent secondError = new AnthropicApi.ErrorEvent(AnthropicApi.EventType.ERROR,
				new AnthropicApi.ErrorEvent.Error("server_error", "Internal server error"));

		AnthropicApi.ChatCompletionResponse response1 = streamHelper.eventToChatCompletionResponse(firstError,
				contentBlockReference);
		AnthropicApi.ChatCompletionResponse response2 = streamHelper.eventToChatCompletionResponse(secondError,
				contentBlockReference);

		assertThat(response1).isNotNull();
		assertThat(response2).isNotNull();
	}

	@Test
	void testMessageStartEvent() {
		StreamHelper streamHelper = new StreamHelper();
		AtomicReference<ChatCompletionResponseBuilder> contentBlockReference = new AtomicReference<>();

		Usage usage = new Usage(10, 20, null, null);
		ChatCompletionResponse message = new ChatCompletionResponse("msg-1", "message", Role.ASSISTANT, List.of(),
				"claude-haiku-4-5", null, null, usage, null);
		MessageStartEvent startEvent = new MessageStartEvent(AnthropicApi.EventType.MESSAGE_START, message);

		ChatCompletionResponse response = streamHelper.eventToChatCompletionResponse(startEvent, contentBlockReference);

		assertThat(response).isNotNull();
		assertThat(response.id()).isEqualTo("msg-1");
		assertThat(response.type()).isEqualTo("MESSAGE_START");
		assertThat(response.role()).isEqualTo(Role.ASSISTANT);
		assertThat(response.model()).isEqualTo("claude-haiku-4-5");
		assertThat(response.usage().inputTokens()).isEqualTo(10);
		assertThat(response.usage().outputTokens()).isEqualTo(20);
	}

	@Test
	void testContentBlockStartTextEvent() {
		StreamHelper streamHelper = new StreamHelper();
		AtomicReference<ChatCompletionResponseBuilder> contentBlockReference = new AtomicReference<>();

		Usage usage = new Usage(0, 0, null, null);
		ChatCompletionResponse message = new ChatCompletionResponse("msg-1", "message", Role.ASSISTANT, List.of(),
				"claude-haiku-4-5", null, null, usage, null);
		MessageStartEvent startEvent = new MessageStartEvent(AnthropicApi.EventType.MESSAGE_START, message);
		streamHelper.eventToChatCompletionResponse(startEvent, contentBlockReference);

		ContentBlockStartEvent.ContentBlockText textBlock = new ContentBlockStartEvent.ContentBlockText("text",
				"Hello");
		ContentBlockStartEvent textStartEvent = new ContentBlockStartEvent(AnthropicApi.EventType.CONTENT_BLOCK_START,
				0, textBlock);

		ChatCompletionResponse response = streamHelper.eventToChatCompletionResponse(textStartEvent,
				contentBlockReference);

		assertThat(response).isNotNull();
		assertThat(response.type()).isEqualTo("CONTENT_BLOCK_START");
		assertThat(response.content()).hasSize(1);
		assertThat(response.content().get(0).type()).isEqualTo(ContentBlock.Type.TEXT);
		assertThat(response.content().get(0).text()).isEqualTo("Hello");
	}

	@Test
	void testContentBlockDeltaTextEvent() {
		StreamHelper streamHelper = new StreamHelper();
		AtomicReference<ChatCompletionResponseBuilder> contentBlockReference = new AtomicReference<>();

		Usage usage = new Usage(0, 0, null, null);
		ChatCompletionResponse message = new ChatCompletionResponse("msg-1", "message", Role.ASSISTANT, List.of(),
				"claude-haiku-4-5", null, null, usage, null);
		MessageStartEvent startEvent = new MessageStartEvent(AnthropicApi.EventType.MESSAGE_START, message);
		streamHelper.eventToChatCompletionResponse(startEvent, contentBlockReference);

		ContentBlockDeltaEvent.ContentBlockDeltaText deltaText = new ContentBlockDeltaEvent.ContentBlockDeltaText(
				"text_delta", " world!");
		ContentBlockDeltaEvent deltaEvent = new ContentBlockDeltaEvent(AnthropicApi.EventType.CONTENT_BLOCK_DELTA, 0,
				deltaText);

		ChatCompletionResponse response = streamHelper.eventToChatCompletionResponse(deltaEvent, contentBlockReference);

		assertThat(response).isNotNull();
		assertThat(response.type()).isEqualTo("CONTENT_BLOCK_DELTA");
		assertThat(response.content()).hasSize(1);
		assertThat(response.content().get(0).type()).isEqualTo(ContentBlock.Type.TEXT_DELTA);
		assertThat(response.content().get(0).text()).isEqualTo(" world!");
	}

	@Test
	void testMessageStopEvent() {
		StreamHelper streamHelper = new StreamHelper();
		AtomicReference<ChatCompletionResponseBuilder> contentBlockReference = new AtomicReference<>();

		Usage usage = new Usage(0, 0, null, null);
		ChatCompletionResponse message = new ChatCompletionResponse("msg-1", "message", Role.ASSISTANT, List.of(),
				"claude-haiku-4-5", null, null, usage, null);
		MessageStartEvent startEvent = new MessageStartEvent(AnthropicApi.EventType.MESSAGE_START, message);
		streamHelper.eventToChatCompletionResponse(startEvent, contentBlockReference);

		MessageStopEvent stopEvent = new MessageStopEvent(AnthropicApi.EventType.MESSAGE_STOP);

		ChatCompletionResponse response = streamHelper.eventToChatCompletionResponse(stopEvent, contentBlockReference);

		assertThat(response).isNotNull();
		assertThat(response.type()).isEqualTo("MESSAGE_STOP");
		assertThat(response.content()).isEmpty();
		assertThat(response.stopReason()).isNull();
		assertThat(response.stopSequence()).isNull();
	}

	@Test
	void testMessageDeltaEvent() {
		StreamHelper streamHelper = new StreamHelper();
		AtomicReference<ChatCompletionResponseBuilder> contentBlockReference = new AtomicReference<>();

		Usage usage = new Usage(0, 0, null, null);
		ChatCompletionResponse initialMessage = new ChatCompletionResponse("msg-1", "message", Role.ASSISTANT,
				List.of(), "claude-haiku-4-5", null, null, usage, null);
		MessageStartEvent startEvent = new MessageStartEvent(AnthropicApi.EventType.MESSAGE_START, initialMessage);
		streamHelper.eventToChatCompletionResponse(startEvent, contentBlockReference);

		MessageDeltaEvent.MessageDelta delta = new MessageDeltaEvent.MessageDelta("end_turn", null);
		MessageDeltaEvent.MessageDeltaUsage deltaUsage = new MessageDeltaEvent.MessageDeltaUsage(15);
		MessageDeltaEvent deltaEvent = new MessageDeltaEvent(AnthropicApi.EventType.MESSAGE_DELTA, delta, deltaUsage);

		ChatCompletionResponse response = streamHelper.eventToChatCompletionResponse(deltaEvent, contentBlockReference);

		assertThat(response).isNotNull();
		assertThat(response.type()).isEqualTo("MESSAGE_DELTA");
		assertThat(response.stopReason()).isEqualTo("end_turn");
		assertThat(response.usage().outputTokens()).isEqualTo(15);
	}

	@Test
	void testPingEvent() {
		StreamHelper streamHelper = new StreamHelper();
		AtomicReference<ChatCompletionResponseBuilder> contentBlockReference = new AtomicReference<>();

		Usage usage = new Usage(0, 0, null, null);
		ChatCompletionResponse message = new ChatCompletionResponse("msg-1", "message", Role.ASSISTANT, List.of(),
				"claude-haiku-4-5", null, null, usage, null);
		MessageStartEvent startEvent = new MessageStartEvent(AnthropicApi.EventType.MESSAGE_START, message);
		streamHelper.eventToChatCompletionResponse(startEvent, contentBlockReference);

		PingEvent pingEvent = new PingEvent(AnthropicApi.EventType.PING);

		ChatCompletionResponse response = streamHelper.eventToChatCompletionResponse(pingEvent, contentBlockReference);

		assertThat(response).isNotNull();
		assertThat(response.type()).isEqualTo("PING");
		assertThat(response.content()).isEmpty();
	}

	@Test
	void testToolUseAggregateEvent() {
		StreamHelper streamHelper = new StreamHelper();
		AtomicReference<ChatCompletionResponseBuilder> contentBlockReference = new AtomicReference<>();

		Usage usage = new Usage(0, 0, null, null);
		ChatCompletionResponse message = new ChatCompletionResponse("msg-1", "message", Role.ASSISTANT, List.of(),
				"claude-haiku-4-5", null, null, usage, null);
		MessageStartEvent startEvent = new MessageStartEvent(AnthropicApi.EventType.MESSAGE_START, message);
		streamHelper.eventToChatCompletionResponse(startEvent, contentBlockReference);

		AnthropicApi.ToolUseAggregationEvent toolEvent = new AnthropicApi.ToolUseAggregationEvent();

		ContentBlockStartEvent.ContentBlockToolUse toolUse = new ContentBlockStartEvent.ContentBlockToolUse("tool_use",
				"tool-1", "calculator", Map.of("operation", "add", "x", 2, "y", 3));

		try {
			Field toolContentBlocksField = AnthropicApi.ToolUseAggregationEvent.class
				.getDeclaredField("toolContentBlocks");
			toolContentBlocksField.setAccessible(true);
			@SuppressWarnings("unchecked")
			List<ContentBlockStartEvent.ContentBlockToolUse> toolContentBlocks = (List<ContentBlockStartEvent.ContentBlockToolUse>) toolContentBlocksField
				.get(toolEvent);
			toolContentBlocks.add(toolUse);

			Field indexField = AnthropicApi.ToolUseAggregationEvent.class.getDeclaredField("index");
			indexField.setAccessible(true);
			indexField.set(toolEvent, 0);

			Field idField = AnthropicApi.ToolUseAggregationEvent.class.getDeclaredField("id");
			idField.setAccessible(true);
			idField.set(toolEvent, "tool-1");

			Field nameField = AnthropicApi.ToolUseAggregationEvent.class.getDeclaredField("name");
			nameField.setAccessible(true);
			nameField.set(toolEvent, "calculator");
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

		ChatCompletionResponse response = streamHelper.eventToChatCompletionResponse(toolEvent, contentBlockReference);

		assertThat(response).isNotNull();
		assertThat(response.content()).hasSize(1);
		assertThat(response.content().get(0).type()).isEqualTo(ContentBlock.Type.TOOL_USE);
		assertThat(response.content().get(0).id()).isEqualTo("tool-1");
		assertThat(response.content().get(0).name()).isEqualTo("calculator");
		assertThat(response.content().get(0).input()).containsEntry("operation", "add");
	}

	@Test
	void testContentBlockStartThinkingEvent() {
		StreamHelper streamHelper = new StreamHelper();
		AtomicReference<ChatCompletionResponseBuilder> contentBlockReference = new AtomicReference<>();

		Usage usage = new Usage(0, 0, null, null);
		ChatCompletionResponse message = new ChatCompletionResponse("msg-1", "message", Role.ASSISTANT, List.of(),
				"claude-haiku-4-5", null, null, usage, null);
		MessageStartEvent startEvent = new MessageStartEvent(AnthropicApi.EventType.MESSAGE_START, message);
		streamHelper.eventToChatCompletionResponse(startEvent, contentBlockReference);

		ContentBlockStartEvent.ContentBlockThinking thinkingBlock = new ContentBlockStartEvent.ContentBlockThinking(
				"thinking", "Initial thinking content", "signature123");
		ContentBlockStartEvent thinkingStartEvent = new ContentBlockStartEvent(
				AnthropicApi.EventType.CONTENT_BLOCK_START, 0, thinkingBlock);

		ChatCompletionResponse response = streamHelper.eventToChatCompletionResponse(thinkingStartEvent,
				contentBlockReference);

		assertThat(response).isNotNull();
		assertThat(response.type()).isEqualTo("CONTENT_BLOCK_START");
		assertThat(response.content()).hasSize(1);
		assertThat(response.content().get(0).type()).isEqualTo(ContentBlock.Type.THINKING);
		assertThat(response.content().get(0).thinking()).isEqualTo("Initial thinking content");
		assertThat(response.content().get(0).signature()).isEqualTo("signature123");
	}

	@Test
	void testContentBlockDeltaThinkingEvent() {
		StreamHelper streamHelper = new StreamHelper();
		AtomicReference<ChatCompletionResponseBuilder> contentBlockReference = new AtomicReference<>();

		Usage usage = new Usage(0, 0, null, null);
		ChatCompletionResponse message = new ChatCompletionResponse("msg-1", "message", Role.ASSISTANT, List.of(),
				"claude-haiku-4-5", null, null, usage, null);
		MessageStartEvent startEvent = new MessageStartEvent(AnthropicApi.EventType.MESSAGE_START, message);
		streamHelper.eventToChatCompletionResponse(startEvent, contentBlockReference);

		ContentBlockDeltaEvent.ContentBlockDeltaThinking deltaThinking = new ContentBlockDeltaEvent.ContentBlockDeltaThinking(
				"thinking_delta", "Additional thinking content");
		ContentBlockDeltaEvent deltaEvent = new ContentBlockDeltaEvent(AnthropicApi.EventType.CONTENT_BLOCK_DELTA, 0,
				deltaThinking);

		ChatCompletionResponse response = streamHelper.eventToChatCompletionResponse(deltaEvent, contentBlockReference);

		assertThat(response).isNotNull();
		assertThat(response.type()).isEqualTo("CONTENT_BLOCK_DELTA");
		assertThat(response.content()).hasSize(1);
		assertThat(response.content().get(0).type()).isEqualTo(ContentBlock.Type.THINKING_DELTA);
		assertThat(response.content().get(0).thinking()).isEqualTo("Additional thinking content");
	}

	@Test
	void testContentBlockDeltaSignatureEvent() {
		StreamHelper streamHelper = new StreamHelper();
		AtomicReference<ChatCompletionResponseBuilder> contentBlockReference = new AtomicReference<>();

		Usage usage = new Usage(0, 0, null, null);
		ChatCompletionResponse message = new ChatCompletionResponse("msg-1", "message", Role.ASSISTANT, List.of(),
				"claude-haiku-4-5", null, null, usage, null);
		MessageStartEvent startEvent = new MessageStartEvent(AnthropicApi.EventType.MESSAGE_START, message);
		streamHelper.eventToChatCompletionResponse(startEvent, contentBlockReference);

		ContentBlockDeltaEvent.ContentBlockDeltaSignature deltaSignature = new ContentBlockDeltaEvent.ContentBlockDeltaSignature(
				"signature_delta", "signature456");
		ContentBlockDeltaEvent deltaEvent = new ContentBlockDeltaEvent(AnthropicApi.EventType.CONTENT_BLOCK_DELTA, 0,
				deltaSignature);

		ChatCompletionResponse response = streamHelper.eventToChatCompletionResponse(deltaEvent, contentBlockReference);

		assertThat(response).isNotNull();
		assertThat(response.type()).isEqualTo("CONTENT_BLOCK_DELTA");
		assertThat(response.content()).hasSize(1);
		assertThat(response.content().get(0).type()).isEqualTo(ContentBlock.Type.SIGNATURE_DELTA);
		assertThat(response.content().get(0).signature()).isEqualTo("signature456");
	}

	@Test
	void testContentBlockStopEvent() {
		StreamHelper streamHelper = new StreamHelper();
		AtomicReference<ChatCompletionResponseBuilder> contentBlockReference = new AtomicReference<>();

		Usage usage = new Usage(0, 0, null, null);
		ChatCompletionResponse message = new ChatCompletionResponse("msg-1", "message", Role.ASSISTANT, List.of(),
				"claude-haiku-4-5", null, null, usage, null);
		MessageStartEvent startEvent = new MessageStartEvent(AnthropicApi.EventType.MESSAGE_START, message);
		streamHelper.eventToChatCompletionResponse(startEvent, contentBlockReference);

		AnthropicApi.ContentBlockStopEvent stopEvent = new AnthropicApi.ContentBlockStopEvent(
				AnthropicApi.EventType.CONTENT_BLOCK_STOP, 0);

		ChatCompletionResponse response = streamHelper.eventToChatCompletionResponse(stopEvent, contentBlockReference);

		assertThat(response).isNotNull();
	}

	@Test
	void testUnsupportedContentBlockType() {
		StreamHelper streamHelper = new StreamHelper();
		AtomicReference<ChatCompletionResponseBuilder> contentBlockReference = new AtomicReference<>();

		Usage usage = new Usage(0, 0, null, null);
		ChatCompletionResponse message = new ChatCompletionResponse("msg-1", "message", Role.ASSISTANT, List.of(),
				"claude-haiku-4-5", null, null, usage, null);
		MessageStartEvent startEvent = new MessageStartEvent(AnthropicApi.EventType.MESSAGE_START, message);
		streamHelper.eventToChatCompletionResponse(startEvent, contentBlockReference);

		ContentBlockStartEvent.ContentBlockBody unsupportedBlock = () -> "unsupported_type";

		ContentBlockStartEvent unsupportedEvent = new ContentBlockStartEvent(AnthropicApi.EventType.CONTENT_BLOCK_START,
				0, unsupportedBlock);

		assertThatThrownBy(() -> streamHelper.eventToChatCompletionResponse(unsupportedEvent, contentBlockReference))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Unsupported content block type");
	}

	@Test
	void testUnsupportedContentBlockDeltaType() {
		StreamHelper streamHelper = new StreamHelper();
		AtomicReference<ChatCompletionResponseBuilder> contentBlockReference = new AtomicReference<>();

		Usage usage = new Usage(0, 0, null, null);
		ChatCompletionResponse message = new ChatCompletionResponse("msg-1", "message", Role.ASSISTANT, List.of(),
				"claude-haiku-4-5", null, null, usage, null);
		MessageStartEvent startEvent = new MessageStartEvent(AnthropicApi.EventType.MESSAGE_START, message);
		streamHelper.eventToChatCompletionResponse(startEvent, contentBlockReference);

		ContentBlockDeltaEvent.ContentBlockDeltaBody unsupportedDelta = () -> "unsupported_delta_type";

		ContentBlockDeltaEvent unsupportedEvent = new ContentBlockDeltaEvent(AnthropicApi.EventType.CONTENT_BLOCK_DELTA,
				0, unsupportedDelta);

		assertThatThrownBy(() -> streamHelper.eventToChatCompletionResponse(unsupportedEvent, contentBlockReference))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Unsupported content block delta type");
	}

	@Test
	void testToolUseAggregationWithEmptyToolContentBlocks() {
		StreamHelper streamHelper = new StreamHelper();
		AtomicReference<ChatCompletionResponseBuilder> contentBlockReference = new AtomicReference<>();

		Usage usage = new Usage(0, 0, null, null);
		ChatCompletionResponse message = new ChatCompletionResponse("msg-1", "message", Role.ASSISTANT, List.of(),
				"claude-haiku-4-5", null, null, usage, null);
		MessageStartEvent startEvent = new MessageStartEvent(AnthropicApi.EventType.MESSAGE_START, message);
		streamHelper.eventToChatCompletionResponse(startEvent, contentBlockReference);

		AnthropicApi.ToolUseAggregationEvent toolEvent = new AnthropicApi.ToolUseAggregationEvent();

		try {
			Field toolContentBlocksField = AnthropicApi.ToolUseAggregationEvent.class
				.getDeclaredField("toolContentBlocks");
			toolContentBlocksField.setAccessible(true);
			@SuppressWarnings("unchecked")
			List<ContentBlockStartEvent.ContentBlockToolUse> toolContentBlocks = (List<ContentBlockStartEvent.ContentBlockToolUse>) toolContentBlocksField
				.get(toolEvent);
			toolContentBlocks.clear(); // 清空列表

			Field indexField = AnthropicApi.ToolUseAggregationEvent.class.getDeclaredField("index");
			indexField.setAccessible(true);
			indexField.set(toolEvent, null);
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

		ChatCompletionResponse response = streamHelper.eventToChatCompletionResponse(toolEvent, contentBlockReference);

		assertThat(response).isNotNull();
	}

	@Test
	void testMergeToolUseEventsWithNonToolUseAggregationEvent() {
		StreamHelper streamHelper = new StreamHelper();

		AnthropicApi.StreamEvent previousEvent = new AnthropicApi.PingEvent(AnthropicApi.EventType.PING);
		AnthropicApi.StreamEvent currentEvent = new AnthropicApi.PingEvent(AnthropicApi.EventType.PING);

		AnthropicApi.StreamEvent result = streamHelper.mergeToolUseEvents(previousEvent, currentEvent);

		assertThat(result).isEqualTo(currentEvent);
	}

	@Test
	void testIsToolUseStartWithNullEvent() {
		StreamHelper streamHelper = new StreamHelper();
		assertThat(streamHelper.isToolUseStart(null)).isFalse();
	}

	@Test
	void testIsToolUseStartWithNonContentBlockStartEvent() {
		StreamHelper streamHelper = new StreamHelper();
		AnthropicApi.PingEvent pingEvent = new AnthropicApi.PingEvent(AnthropicApi.EventType.PING);
		assertThat(streamHelper.isToolUseStart(pingEvent)).isFalse();
	}

	@Test
	void testIsToolUseFinishWithNullEvent() {
		StreamHelper streamHelper = new StreamHelper();
		assertThat(streamHelper.isToolUseFinish(null)).isFalse();
	}

	@Test
	void testIsToolUseFinishWithNonContentBlockStopEvent() {
		StreamHelper streamHelper = new StreamHelper();
		AnthropicApi.PingEvent pingEvent = new AnthropicApi.PingEvent(AnthropicApi.EventType.PING);
		assertThat(streamHelper.isToolUseFinish(pingEvent)).isFalse();
	}

	@Test
	void testIsToolUseFinishWithContentBlockStopEvent() {
		StreamHelper streamHelper = new StreamHelper();
		AnthropicApi.ContentBlockStopEvent stopEvent = new AnthropicApi.ContentBlockStopEvent(
				AnthropicApi.EventType.CONTENT_BLOCK_STOP, 0);
		assertThat(streamHelper.isToolUseFinish(stopEvent)).isTrue();
	}

	@Test
	void testPingEventHandling() {
		StreamHelper streamHelper = new StreamHelper();
		AtomicReference<ChatCompletionResponseBuilder> contentBlockReference = new AtomicReference<>();

		// Initialize content block reference with a message start event. This ensures
		// that message id, model and content are set in the contentBlockReference
		ChatCompletionResponse message = new ChatCompletionResponse("msg-1", "message", Role.ASSISTANT, List.of(),
				"claude-haiku-4-5", null, null, null, null);
		streamHelper.eventToChatCompletionResponse(new MessageStartEvent(AnthropicApi.EventType.MESSAGE_START, message),
				contentBlockReference);

		AnthropicApi.PingEvent pingEvent = new AnthropicApi.PingEvent(AnthropicApi.EventType.PING);

		AnthropicApi.ChatCompletionResponse response = streamHelper.eventToChatCompletionResponse(pingEvent,
				contentBlockReference);

		assertThat(response).isNotNull();
	}

}
