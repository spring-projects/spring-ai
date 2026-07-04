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

package org.springframework.ai.anthropic.api;

import java.io.IOException;
import java.util.List;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.anthropic.api.AnthropicApi.AnthropicMessage;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionResponse;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock;
import org.springframework.ai.anthropic.api.AnthropicApi.EventType;
import org.springframework.ai.anthropic.api.AnthropicApi.Role;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AnthropicApi#chatCompletionStream} event handling against a mock
 * server, without requiring real API calls.
 *
 * @author Nikita Kibitkin
 */
class AnthropicApiChatCompletionStreamTests {

	private MockWebServer mockWebServer;

	private AnthropicApi anthropicApi;

	@BeforeEach
	void setUp() throws IOException {
		this.mockWebServer = new MockWebServer();
		this.mockWebServer.start();
		this.anthropicApi = AnthropicApi.builder()
			.apiKey("test-api-key")
			.baseUrl(this.mockWebServer.url("/").toString())
			.build();
	}

	@AfterEach
	void tearDown() throws IOException {
		this.mockWebServer.shutdown();
	}

	// GH-6372
	@Test
	void contentBlockStopEventShouldNotProduceResponseChunk() {
		String sseBody = """
				event: message_start
				data: {"type":"message_start","message":{"id":"msg_123","type":"message","role":"assistant","model":"claude-sonnet-4-5","content":[],"usage":{"input_tokens":10,"output_tokens":1}}}

				event: content_block_start
				data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

				event: ping
				data: {"type":"ping"}

				event: content_block_delta
				data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}

				event: content_block_delta
				data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" world"}}

				event: content_block_stop
				data: {"type":"content_block_stop","index":0}

				event: message_delta
				data: {"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":12}}

				event: message_stop
				data: {"type":"message_stop"}

				""";

		this.mockWebServer.enqueue(new MockResponse().setBody(sseBody)
			.setHeader("Content-Type", "text/event-stream")
			.setHeader("Cache-Control", "no-cache"));

		List<ChatCompletionResponse> responses = this.anthropicApi.chatCompletionStream(streamRequest())
			.collectList()
			.block();

		assertThat(responses).isNotEmpty();
		assertThat(responses)
			.noneSatisfy(response -> assertThat(response.type()).isEqualTo(EventType.CONTENT_BLOCK_STOP.name()));

		String aggregatedText = responses.stream()
			.flatMap(response -> response.content().stream())
			.filter(contentBlock -> contentBlock.text() != null)
			.map(ContentBlock::text)
			.reduce("", String::concat);
		assertThat(aggregatedText).isEqualTo("Hello world");
	}

	// GH-6372
	@Test
	void contentBlockStopEventShouldStillFinalizeToolUseAggregation() {
		String sseBody = """
				event: message_start
				data: {"type":"message_start","message":{"id":"msg_456","type":"message","role":"assistant","model":"claude-sonnet-4-5","content":[],"usage":{"input_tokens":10,"output_tokens":1}}}

				event: content_block_start
				data: {"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"toolu_1","name":"getWeather","input":{}}}

				event: content_block_delta
				data: {"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\\"location\\":"}}

				event: content_block_delta
				data: {"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"\\"Paris\\"}"}}

				event: content_block_stop
				data: {"type":"content_block_stop","index":0}

				event: message_delta
				data: {"type":"message_delta","delta":{"stop_reason":"tool_use","stop_sequence":null},"usage":{"output_tokens":30}}

				event: message_stop
				data: {"type":"message_stop"}

				""";

		this.mockWebServer.enqueue(new MockResponse().setBody(sseBody)
			.setHeader("Content-Type", "text/event-stream")
			.setHeader("Cache-Control", "no-cache"));

		List<ChatCompletionResponse> responses = this.anthropicApi.chatCompletionStream(streamRequest())
			.collectList()
			.block();

		assertThat(responses).isNotEmpty();
		assertThat(responses)
			.noneSatisfy(response -> assertThat(response.type()).isEqualTo(EventType.CONTENT_BLOCK_STOP.name()));

		// The message_delta chunk must still carry the aggregated tool call, as tool
		// execution downstream is triggered by its stop reason.
		ChatCompletionResponse messageDeltaResponse = responses.stream()
			.filter(response -> EventType.MESSAGE_DELTA.name().equals(response.type()))
			.findFirst()
			.orElseThrow();
		assertThat(messageDeltaResponse.stopReason()).isEqualTo("tool_use");
		assertThat(messageDeltaResponse.content()).hasSize(1);
		assertThat(messageDeltaResponse.content().get(0).type()).isEqualTo(ContentBlock.Type.TOOL_USE);
		assertThat(messageDeltaResponse.content().get(0).name()).isEqualTo("getWeather");
		assertThat(messageDeltaResponse.content().get(0).input()).containsEntry("location", "Paris");
	}

	private ChatCompletionRequest streamRequest() {
		return ChatCompletionRequest.builder()
			.model(AnthropicApi.ChatModel.CLAUDE_SONNET_4_5)
			.messages(List.of(new AnthropicMessage(List.of(new ContentBlock("Hi")), Role.USER)))
			.maxTokens(100)
			.stream(true)
			.build();
	}

}
