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

package org.springframework.ai.openai.api;

import java.util.List;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for streaming chat completion error handling in {@link OpenAiApi}. Tests the
 * behavior when the LLM returns malformed JSON chunks.
 *
 * @author Liu Guodong
 */
class OpenAiStreamErrorHandlingTest {

	private MockWebServer mockWebServer;

	private OpenAiApi openAiApi;

	@BeforeEach
	void setUp() throws Exception {
		this.mockWebServer = new MockWebServer();
		this.mockWebServer.start();
	}

	@AfterEach
	void tearDown() throws Exception {
		if (this.mockWebServer != null) {
			this.mockWebServer.shutdown();
		}
	}

	@Test
	void testSkipStrategy_shouldSkipInvalidChunks() {
		// Arrange
		String validChunk1 = """
				{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-3.5-turbo","choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}
				""";
		String invalidChunk = "invalid json {";
		String validChunk2 = """
				{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-3.5-turbo","choices":[{"index":0,"delta":{"content":" world"},"finish_reason":null}]}
				""";

		this.mockWebServer.enqueue(new MockResponse().setResponseCode(200)
			.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
			.setBody(validChunk1 + invalidChunk + validChunk2 + "[DONE]"));

		this.openAiApi = OpenAiApi.builder()
			.apiKey("test-key")
			.baseUrl(this.mockWebServer.url("/").toString())
			.streamErrorHandlingStrategy(StreamErrorHandlingStrategy.SKIP)
			.build();

		ChatCompletionMessage message = new ChatCompletionMessage("Test", ChatCompletionMessage.Role.USER);
		ChatCompletionRequest request = new ChatCompletionRequest(List.of(message), "gpt-3.5-turbo", 0.8, true);

		// Act
		Flux<ChatCompletionChunk> result = this.openAiApi.chatCompletionStream(request);

		// Assert - should receive 2 valid chunks, invalid one is skipped
		StepVerifier.create(result)
			.expectNextMatches(chunk -> chunk.choices() != null && chunk.choices().size() > 0)
			.expectNextMatches(chunk -> chunk.choices() != null && chunk.choices().size() > 0)
			.verifyComplete();
	}

	@Test
	void testFailFastStrategy_shouldThrowErrorOnInvalidChunk() {
		// Arrange
		String validChunk = """
				{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-3.5-turbo","choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}
				""";
		String invalidChunk = "invalid json {";

		this.mockWebServer.enqueue(new MockResponse().setResponseCode(200)
			.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
			.setBody(validChunk + invalidChunk + "[DONE]"));

		this.openAiApi = OpenAiApi.builder()
			.apiKey("test-key")
			.baseUrl(this.mockWebServer.url("/").toString())
			.streamErrorHandlingStrategy(StreamErrorHandlingStrategy.FAIL_FAST)
			.build();

		ChatCompletionMessage message = new ChatCompletionMessage("Test", ChatCompletionMessage.Role.USER);
		ChatCompletionRequest request = new ChatCompletionRequest(List.of(message), "gpt-3.5-turbo", 0.8, true);

		// Act
		Flux<ChatCompletionChunk> result = this.openAiApi.chatCompletionStream(request);

		// Assert - should receive 1 valid chunk then error
		StepVerifier.create(result)
			.expectNextMatches(chunk -> chunk.choices() != null && chunk.choices().size() > 0)
			.expectError(ChatCompletionParseException.class)
			.verify();
	}

	@Test
	void testLogAndContinueStrategy_shouldLogAndSkipInvalidChunks() {
		// Arrange
		String validChunk1 = """
				{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-3.5-turbo","choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}
				""";
		String invalidChunk = "{incomplete";
		String validChunk2 = """
				{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-3.5-turbo","choices":[{"index":0,"delta":{"content":" world"},"finish_reason":"stop"}]}
				""";

		this.mockWebServer.enqueue(new MockResponse().setResponseCode(200)
			.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
			.setBody(validChunk1 + invalidChunk + validChunk2 + "[DONE]"));

		this.openAiApi = OpenAiApi.builder()
			.apiKey("test-key")
			.baseUrl(this.mockWebServer.url("/").toString())
			.streamErrorHandlingStrategy(StreamErrorHandlingStrategy.LOG_AND_CONTINUE)
			.build();

		ChatCompletionMessage message = new ChatCompletionMessage("Test", ChatCompletionMessage.Role.USER);
		ChatCompletionRequest request = new ChatCompletionRequest(List.of(message), "gpt-3.5-turbo", 0.8, true);

		// Act
		Flux<ChatCompletionChunk> result = this.openAiApi.chatCompletionStream(request);

		// Assert - should receive 2 valid chunks, invalid one is logged and skipped
		StepVerifier.create(result).expectNextCount(2).verifyComplete();
	}

	@Test
	void testDefaultStrategy_shouldBeSkip() {
		// Arrange
		this.openAiApi = OpenAiApi.builder().apiKey("test-key").baseUrl(this.mockWebServer.url("/").toString()).build();

		String validChunk = """
				{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-3.5-turbo","choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}
				""";
		String invalidChunk = "not valid json";

		this.mockWebServer.enqueue(new MockResponse().setResponseCode(200)
			.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
			.setBody(validChunk + invalidChunk + "[DONE]"));

		ChatCompletionMessage message = new ChatCompletionMessage("Test", ChatCompletionMessage.Role.USER);
		ChatCompletionRequest request = new ChatCompletionRequest(List.of(message), "gpt-3.5-turbo", 0.8, true);

		// Act
		Flux<ChatCompletionChunk> result = this.openAiApi.chatCompletionStream(request);

		// Assert - default strategy should skip invalid chunks
		StepVerifier.create(result).expectNextCount(1).verifyComplete();
	}

	@Test
	void testAllValidChunks_shouldProcessNormally() {
		// Arrange
		String validChunk1 = """
				{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-3.5-turbo","choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}
				""";
		String validChunk2 = """
				{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-3.5-turbo","choices":[{"index":0,"delta":{"content":" world"},"finish_reason":"stop"}]}
				""";

		this.mockWebServer.enqueue(new MockResponse().setResponseCode(200)
			.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
			.setBody(validChunk1 + validChunk2 + "[DONE]"));

		this.openAiApi = OpenAiApi.builder()
			.apiKey("test-key")
			.baseUrl(this.mockWebServer.url("/").toString())
			.streamErrorHandlingStrategy(StreamErrorHandlingStrategy.SKIP)
			.build();

		ChatCompletionMessage message = new ChatCompletionMessage("Test", ChatCompletionMessage.Role.USER);
		ChatCompletionRequest request = new ChatCompletionRequest(List.of(message), "gpt-3.5-turbo", 0.8, true);

		// Act
		Flux<ChatCompletionChunk> result = this.openAiApi.chatCompletionStream(request);

		// Assert
		StepVerifier.create(result).expectNextCount(2).verifyComplete();
	}

	@Test
	void testChatCompletionParseException_shouldContainRawContent() {
		// Arrange
		String rawContent = "invalid json content";
		Exception cause = new RuntimeException("Parse error");

		// Act
		ChatCompletionParseException exception = new ChatCompletionParseException("Test error", rawContent, cause);

		// Assert
		assertThat(exception.getRawContent()).isEqualTo(rawContent);
		assertThat(exception.getMessage()).isEqualTo("Test error");
		assertThat(exception.getCause()).isEqualTo(cause);
	}

	@Test
	void testMutateApi_shouldPreserveErrorHandlingStrategy() {
		// Arrange
		OpenAiApi originalApi = OpenAiApi.builder()
			.apiKey("test-key")
			.baseUrl(this.mockWebServer.url("/").toString())
			.streamErrorHandlingStrategy(StreamErrorHandlingStrategy.FAIL_FAST)
			.build();

		// Act
		OpenAiApi mutatedApi = originalApi.mutate().apiKey("new-key").build();

		String validChunk = """
				{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-3.5-turbo","choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}
				""";
		String invalidChunk = "invalid";

		this.mockWebServer.enqueue(new MockResponse().setResponseCode(200)
			.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
			.setBody(validChunk + invalidChunk + "[DONE]"));

		ChatCompletionMessage message = new ChatCompletionMessage("Test", ChatCompletionMessage.Role.USER);
		ChatCompletionRequest request = new ChatCompletionRequest(List.of(message), "gpt-3.5-turbo", 0.8, true);

		// Act
		Flux<ChatCompletionChunk> result = mutatedApi.chatCompletionStream(request);

		// Assert - should still use FAIL_FAST strategy
		StepVerifier.create(result).expectNextCount(1).expectError(ChatCompletionParseException.class).verify();
	}

	@Test
	void testSetStreamErrorHandlingStrategy_shouldUpdateStrategy() {
		// Arrange
		this.openAiApi = OpenAiApi.builder()
			.apiKey("test-key")
			.baseUrl(this.mockWebServer.url("/").toString())
			.streamErrorHandlingStrategy(StreamErrorHandlingStrategy.SKIP)
			.build();

		// Change strategy to FAIL_FAST
		this.openAiApi.setStreamErrorHandlingStrategy(StreamErrorHandlingStrategy.FAIL_FAST);

		String validChunk = """
				{"id":"chatcmpl-123","object":"chat.completion.chunk","created":1677652288,"model":"gpt-3.5-turbo","choices":[{"index":0,"delta":{"content":"Hello"},"finish_reason":null}]}
				""";
		String invalidChunk = "bad json";

		this.mockWebServer.enqueue(new MockResponse().setResponseCode(200)
			.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE)
			.setBody(validChunk + invalidChunk + "[DONE]"));

		ChatCompletionMessage message = new ChatCompletionMessage("Test", ChatCompletionMessage.Role.USER);
		ChatCompletionRequest request = new ChatCompletionRequest(List.of(message), "gpt-3.5-turbo", 0.8, true);

		// Act
		Flux<ChatCompletionChunk> result = this.openAiApi.chatCompletionStream(request);

		// Assert - should now fail fast
		StepVerifier.create(result).expectNextCount(1).expectError(ChatCompletionParseException.class).verify();
	}

}
