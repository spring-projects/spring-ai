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

package org.springframework.ai.openai.chat;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk.ChunkChoice;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionFinishReason;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;

/**
 * Tests for OpenAI streaming responses with various finish_reason scenarios, particularly
 * focusing on edge cases like empty string finish_reason values.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
public class OpenAiStreamingFinishReasonTests {

	@Mock
	private OpenAiApi openAiApi;

	private OpenAiChatModel chatModel;

	@Test
	void testStreamingWithNullFinishReason() {
		// Setup
		setupChatModel();

		var choice = new ChunkChoice(null, 0, new ChatCompletionMessage("Hello", Role.ASSISTANT), null);
		ChatCompletionChunk chunk = new ChatCompletionChunk("id", List.of(choice), 666L, "model", null, null,
				"chat.completion.chunk", null);

		given(this.openAiApi.chatCompletionStream(isA(ChatCompletionRequest.class), any()))
			.willReturn(Flux.just(chunk));

		// Execute
		Flux<ChatResponse> result = this.chatModel.stream(new Prompt("test"));

		// Verify
		List<ChatResponse> responses = result.collectList().block();
		assertThat(responses).hasSize(1);
		ChatResponse response = responses.get(0);
		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isEqualTo("Hello");
		assertThat(response.getResult().getMetadata().getFinishReason()).isEqualTo("");
	}

	@Test
	void testStreamingWithValidFinishReason() {
		// Setup
		setupChatModel();

		var choice = new ChunkChoice(ChatCompletionFinishReason.STOP, 0,
				new ChatCompletionMessage("Complete response", Role.ASSISTANT), null);
		ChatCompletionChunk chunk = new ChatCompletionChunk("id", List.of(choice), 666L, "model", null, null,
				"chat.completion.chunk", null);

		given(this.openAiApi.chatCompletionStream(isA(ChatCompletionRequest.class), any()))
			.willReturn(Flux.just(chunk));

		// Execute
		Flux<ChatResponse> result = this.chatModel.stream(new Prompt("test"));

		// Verify
		List<ChatResponse> responses = result.collectList().block();
		assertThat(responses).hasSize(1);
		ChatResponse response = responses.get(0);
		assertThat(response).isNotNull();
		assertThat(response.getResult().getOutput().getText()).isEqualTo("Complete response");
		assertThat(response.getResult().getMetadata().getFinishReason()).isEqualTo("STOP");
	}

	@Test
	void testJsonDeserializationWithEmptyStringFinishReason() throws JsonProcessingException {
		// Test the specific JSON from the issue report
		String problematicJson = """
				{
					"id": "chatcmpl-msg_bdrk_012bpm3yfa9inEuftTWYQ46F",
					"object": "chat.completion.chunk",
					"created": 1726239401,
					"model": "claude-3-5-sonnet-20240620",
					"choices": [{
						"index": 0,
						"delta": {
							"role": "assistant",
							"content": "",
							"reasoning_content": ""
						},
						"finish_reason": ""
					}]
				}
				""";

		// This should either work correctly or throw a clear exception
		ChatCompletionChunk chunk = ModelOptionsUtils.jsonToObject(problematicJson, ChatCompletionChunk.class);

		// If deserialization succeeds, verify the structure
		assertThat(chunk).isNotNull();
		assertThat(chunk.choices()).hasSize(1);

		var choice = chunk.choices().get(0);
		assertThat(choice.index()).isEqualTo(0);
		assertThat(choice.delta().content()).isEmpty();

		assertThat(choice.finishReason()).isEqualTo(ChatCompletionFinishReason.UNKNOWN);
	}

	@Test
	void testJsonDeserializationWithNullFinishReason() throws JsonProcessingException {
		// Test with null finish_reason (should work fine)
		String validJson = """
				{
					"id": "chatcmpl-test",
					"object": "chat.completion.chunk",
					"created": 1726239401,
					"model": "gpt-4",
					"choices": [{
						"index": 0,
						"delta": {
							"role": "assistant",
							"content": "Hello",
							"reasoning_content": "test"
						},
						"finish_reason": null
					}]
				}
				""";

		ChatCompletionChunk chunk = ModelOptionsUtils.jsonToObject(validJson, ChatCompletionChunk.class);

		assertThat(chunk).isNotNull();
		assertThat(chunk.choices()).hasSize(1);

		var choice = chunk.choices().get(0);
		assertThat(choice.finishReason()).isNull();
		assertThat(choice.delta().content()).isEqualTo("Hello");
		assertThat(choice.delta().reasoningContent()).isEqualTo("test");
	}

	@Test
	void testStreamingWithEmptyStringFinishReasonUsingMockWebServer() {
		// Setup
		setupChatModel();

		// Simulate the problematic response by creating a chunk that would result from
		// deserializing JSON with empty string finish_reason
		try {
			// Try to create a chunk with what would happen if empty string was
			// deserialized
			var choice = new ChunkChoice(null, 0, new ChatCompletionMessage("", Role.ASSISTANT), null);
			ChatCompletionChunk chunk = new ChatCompletionChunk("chatcmpl-msg_bdrk_012bpm3yfa9inEuftTWYQ46F",
					List.of(choice), 1726239401L, "claude-3-5-sonnet-20240620", null, null, "chat.completion.chunk",
					null);

			given(this.openAiApi.chatCompletionStream(isA(ChatCompletionRequest.class), any()))
				.willReturn(Flux.just(chunk));

			// Execute
			Flux<ChatResponse> result = this.chatModel.stream(new Prompt("test"));

			// Verify that the streaming works even with null finish_reason
			List<ChatResponse> responses = result.collectList().block();
			assertThat(responses).hasSize(1);
			ChatResponse response = responses.get(0);
			assertThat(response).isNotNull();
			assertThat(response.getResult().getMetadata().getFinishReason()).isEqualTo("");

		}
		catch (Exception e) {
			// If this fails, it indicates the issue exists in our processing
			System.out.println("Streaming failed with empty finish_reason: " + e.getMessage());
			throw e;
		}
	}

	@Test
	void testModelOptionsUtilsJsonToObjectWithEmptyFinishReason() {
		// Test the specific method mentioned in the issue
		String jsonWithEmptyFinishReason = """
				{
					"id": "chatcmpl-msg_bdrk_012bpm3yfa9inEuftTWYQ46F",
					"object": "chat.completion.chunk",
					"created": 1726239401,
					"model": "claude-3-5-sonnet-20240620",
					"choices": [{
						"index": 0,
						"delta": {
							"role": "assistant",
							"content": ""
						},
						"finish_reason": ""
					}]
				}
				""";

		ChatCompletionChunk chunk = ModelOptionsUtils.jsonToObject(jsonWithEmptyFinishReason,
				ChatCompletionChunk.class);

		assertThat(chunk).isNotNull();
		assertThat(chunk.choices()).hasSize(1);

		var choice = chunk.choices().get(0);
		// The critical test: how does ModelOptionsUtils handle empty string -> enum?
		assertThat(choice.finishReason()).isEqualTo(ChatCompletionFinishReason.UNKNOWN);
	}

	private void setupChatModel() {
		RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;
		ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();
		ObservationRegistry observationRegistry = ObservationRegistry.NOOP;
		this.chatModel = new OpenAiChatModel(this.openAiApi, OpenAiChatOptions.builder().build(), toolCallingManager,
				retryTemplate, observationRegistry);
	}

}
