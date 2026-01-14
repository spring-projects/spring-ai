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

import java.util.Map;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify that extraBody parameters are correctly included in the HTTP request
 * sent to the OpenAI API. This test captures the actual wire-level JSON to verify the
 * end-to-end flow from OpenAiChatOptions through to the HTTP request body.
 *
 * <p>
 * These tests ensure that extraBody fields are:
 * <ul>
 * <li>Correctly merged from OpenAiChatOptions into ChatCompletionRequest</li>
 * <li>Flattened to the top level of the JSON (not nested under "extra_body")</li>
 * <li>Properly handled when set in default options, runtime options, or both</li>
 * </ul>
 *
 * @author Mark Pollack
 * @see <a href="https://github.com/spring-projects/spring-ai/issues/4867">GitHub Issue
 * #4867</a>
 */
class ExtraBodyWireTest {

	private MockWebServer mockWebServer;

	private final JsonMapper jsonMapper = new JsonMapper();

	@BeforeEach
	void setUp() throws Exception {
		this.mockWebServer = new MockWebServer();
		this.mockWebServer.start();
	}

	@AfterEach
	void tearDown() throws Exception {
		this.mockWebServer.shutdown();
	}

	@Test
	void extraBodyFromRuntimeOptionsAppearsInHttpRequest() throws Exception {
		// Arrange: Mock response
		this.mockWebServer.enqueue(createMockResponse());

		OpenAiApi api = OpenAiApi.builder().apiKey("test-key").baseUrl(this.mockWebServer.url("/").toString()).build();

		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiApi(api)
			.defaultOptions(OpenAiChatOptions.builder().model("gpt-4").build())
			.build();

		// Act: Call with extraBody in runtime options
		OpenAiChatOptions runtimeOptions = OpenAiChatOptions.builder()
			.extraBody(Map.of("top_k", 50, "repetition_penalty", 1.1))
			.build();

		chatModel.call(new Prompt("Hello", runtimeOptions));

		// Assert: Verify the wire-level JSON contains flattened extraBody fields
		RecordedRequest recordedRequest = this.mockWebServer.takeRequest();
		String requestBody = recordedRequest.getBody().readUtf8();
		JsonNode json = this.jsonMapper.readTree(requestBody);

		// Verify extraBody fields are at top level
		assertThat(json.has("top_k")).as("top_k should be at top level").isTrue();
		assertThat(json.get("top_k").asInt()).isEqualTo(50);
		assertThat(json.has("repetition_penalty")).as("repetition_penalty should be at top level").isTrue();
		assertThat(json.get("repetition_penalty").asDouble()).isEqualTo(1.1);

		// Verify extra_body is NOT a nested object (fields are flattened)
		assertThat(json.has("extra_body")).as("extra_body should NOT appear as nested object").isFalse();
	}

	@Test
	void extraBodyFromDefaultOptionsAppearsInHttpRequest() throws Exception {
		// Arrange: Mock response
		this.mockWebServer.enqueue(createMockResponse());

		OpenAiApi api = OpenAiApi.builder().apiKey("test-key").baseUrl(this.mockWebServer.url("/").toString()).build();

		// Set extraBody in DEFAULT options
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiApi(api)
			.defaultOptions(OpenAiChatOptions.builder()
				.model("gpt-4")
				.extraBody(Map.of("enable_thinking", true, "top_k", 40))
				.build())
			.build();

		// Act: Call without runtime options
		chatModel.call(new Prompt("Hello"));

		// Assert: Verify wire-level JSON
		RecordedRequest recordedRequest = this.mockWebServer.takeRequest();
		String requestBody = recordedRequest.getBody().readUtf8();
		JsonNode json = this.jsonMapper.readTree(requestBody);

		assertThat(json.has("enable_thinking")).isTrue();
		assertThat(json.get("enable_thinking").asBoolean()).isTrue();
		assertThat(json.has("top_k")).isTrue();
		assertThat(json.get("top_k").asInt()).isEqualTo(40);

		// Verify extra_body is NOT a nested object
		assertThat(json.has("extra_body")).as("extra_body should NOT appear as nested object").isFalse();
	}

	@Test
	void runtimeExtraBodyOverridesDefaultExtraBody() throws Exception {
		// Arrange
		this.mockWebServer.enqueue(createMockResponse());

		OpenAiApi api = OpenAiApi.builder().apiKey("test-key").baseUrl(this.mockWebServer.url("/").toString()).build();

		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiApi(api)
			.defaultOptions(OpenAiChatOptions.builder()
				.model("gpt-4")
				.extraBody(Map.of("top_k", 40, "default_only", "value"))
				.build())
			.build();

		// Act: Runtime extraBody should override default for same key
		OpenAiChatOptions runtimeOptions = OpenAiChatOptions.builder()
			.extraBody(Map.of("top_k", 100, "runtime_only", "value"))
			.build();

		chatModel.call(new Prompt("Hello", runtimeOptions));

		// Assert
		RecordedRequest recordedRequest = this.mockWebServer.takeRequest();
		String requestBody = recordedRequest.getBody().readUtf8();
		JsonNode json = this.jsonMapper.readTree(requestBody);

		// Runtime overrides default
		assertThat(json.get("top_k").asInt()).isEqualTo(100);
		// Both unique keys present
		assertThat(json.has("default_only")).isTrue();
		assertThat(json.has("runtime_only")).isTrue();

		// Verify extra_body is NOT a nested object
		assertThat(json.has("extra_body")).as("extra_body should NOT appear as nested object").isFalse();
	}

	@Test
	void extraBodyWithVllmParameters() throws Exception {
		// Arrange: Test with real vLLM parameters
		this.mockWebServer.enqueue(createMockResponse());

		OpenAiApi api = OpenAiApi.builder().apiKey("test-key").baseUrl(this.mockWebServer.url("/").toString()).build();

		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiApi(api)
			.defaultOptions(OpenAiChatOptions.builder().model("meta-llama/Llama-3-8B-Instruct").build())
			.build();

		// Act: Use real vLLM parameters
		OpenAiChatOptions runtimeOptions = OpenAiChatOptions.builder()
			.extraBody(Map.of("top_k", 50, "min_p", 0.05, "repetition_penalty", 1.1, "best_of", 3))
			.build();

		chatModel.call(new Prompt("Hello", runtimeOptions));

		// Assert
		RecordedRequest recordedRequest = this.mockWebServer.takeRequest();
		String requestBody = recordedRequest.getBody().readUtf8();
		JsonNode json = this.jsonMapper.readTree(requestBody);

		// All vLLM parameters should be at top level
		assertThat(json.get("top_k").asInt()).isEqualTo(50);
		assertThat(json.get("min_p").asDouble()).isEqualTo(0.05);
		assertThat(json.get("repetition_penalty").asDouble()).isEqualTo(1.1);
		assertThat(json.get("best_of").asInt()).isEqualTo(3);

		// Verify model is also set correctly
		assertThat(json.get("model").asText()).isEqualTo("meta-llama/Llama-3-8B-Instruct");
	}

	private MockResponse createMockResponse() {
		return new MockResponse().setResponseCode(200)
			.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.setBody("""
					{
						"id": "chatcmpl-123",
						"object": "chat.completion",
						"created": 1677652288,
						"model": "gpt-4",
						"choices": [{
							"index": 0,
							"message": {"role": "assistant", "content": "Hello!"},
							"finish_reason": "stop"
						}],
						"usage": {"prompt_tokens": 9, "completion_tokens": 2, "total_tokens": 11}
					}
					""");
	}

}
