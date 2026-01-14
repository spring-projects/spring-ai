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
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify JSON serialization behavior of extraBody parameter. This test verifies
 * that @JsonAnyGetter correctly flattens extraBody fields to the top level of the JSON
 * request, matching the behavior expected by OpenAI-compatible servers like vLLM, Ollama,
 * and matching the pattern used by the official OpenAI SDK and LangChain4j.
 */
class ExtraBodySerializationTest {

	@Test
	void testExtraBodySerializationFlattensToTopLevel() throws Exception {
		// Arrange: Create request with extraBody containing vLLM/Ollama parameters
		ChatCompletionRequest request = new ChatCompletionRequest(List.of(), // messages
				"gpt-4", // model
				null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, false,
				null, null, null, null, null, null, null, null, null, null, null, null,
				Map.of("top_k", 50, "repetition_penalty", 1.1) // extraBody
		);

		// Act: Serialize to JSON
		String json = JsonMapper.shared().writerWithDefaultPrettyPrinter().writeValueAsString(request);

		// Assert: Verify @JsonAnyGetter flattens fields to top level
		assertThat(json).contains("\"top_k\" : 50");
		assertThat(json).contains("\"repetition_penalty\" : 1.1");
		assertThat(json).doesNotContain("\"extra_body\"");
	}

	@Test
	void testExtraBodyWithEmptyMap() throws Exception {
		// Arrange: Request with empty extraBody map
		ChatCompletionRequest request = new ChatCompletionRequest(List.of(), // messages
				"gpt-4", // model
				null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, false,
				null, null, null, null, null, null, null, null, null, null, null, null, Map.of() // empty
		// extraBody
		);

		// Act
		String json = JsonMapper.shared().writerWithDefaultPrettyPrinter().writeValueAsString(request);

		// Assert: No extra fields should appear
		assertThat(json).doesNotContain("extra_body");
		assertThat(json).doesNotContain("top_k");
	}

	@Test
	void testExtraBodyNullSerialization() throws Exception {
		// Arrange: Request with null extraBody (normal OpenAI usage)
		ChatCompletionRequest request = new ChatCompletionRequest(List.of(), // messages
				"gpt-4", // model
				null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, false,
				null, null, null, null, null, null, null, null, null, null, null, null, null // extraBody
		// =
		// null
		);

		// Act
		String json = JsonMapper.shared().writerWithDefaultPrettyPrinter().writeValueAsString(request);

		// Assert: extra_body should not appear in JSON when null
		assertThat(json).doesNotContain("extra_body");
		assertThat(json).doesNotContain("top_k");
	}

	@Test
	void testExtraBodyDeserialization() throws Exception {
		// Arrange: JSON with extra fields (simulating proxy server receiving request)
		String json = """
				{
					"model": "gpt-4",
					"messages": [],
					"stream": false,
					"top_k": 50,
					"repetition_penalty": 1.1,
					"custom_param": "test_value"
				}
				""";

		// Act: Deserialize JSON to ChatCompletionRequest
		ChatCompletionRequest request = JsonMapper.shared().readValue(json, ChatCompletionRequest.class);

		// Assert: Extra fields should be captured in extraBody map
		assertThat(request.extraBody()).isNotNull();
		assertThat(request.extraBody()).containsEntry("top_k", 50);
		assertThat(request.extraBody()).containsEntry("repetition_penalty", 1.1);
		assertThat(request.extraBody()).containsEntry("custom_param", "test_value");

		// Assert: Standard fields should be set correctly
		assertThat(request.model()).isEqualTo("gpt-4");
		assertThat(request.messages()).isEmpty();
		assertThat(request.stream()).isFalse();
	}

	@Test
	void testRoundTripSerializationDeserialization() throws Exception {
		// Arrange: Create request with extraBody
		ChatCompletionRequest originalRequest = new ChatCompletionRequest(List.of(), // messages
				"gpt-4", // model
				null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, false,
				null, null, null, null, null, null, null, null, null, null, null, null,
				Map.of("top_k", 50, "min_p", 0.05, "stop_token_ids", List.of(128001, 128009)) // extraBody
		);

		// Act: Serialize to JSON
		String json = JsonMapper.shared().writeValueAsString(originalRequest);

		// Act: Deserialize back to object
		ChatCompletionRequest deserializedRequest = JsonMapper.shared().readValue(json, ChatCompletionRequest.class);

		// Assert: All extraBody fields should survive round trip
		assertThat(deserializedRequest.extraBody()).isNotNull();
		assertThat(deserializedRequest.extraBody()).containsEntry("top_k", 50);
		assertThat(deserializedRequest.extraBody()).containsEntry("min_p", 0.05);
		assertThat(deserializedRequest.extraBody()).containsKey("stop_token_ids");

		// Assert: Standard fields should match
		assertThat(deserializedRequest.model()).isEqualTo(originalRequest.model());
		assertThat(deserializedRequest.stream()).isEqualTo(originalRequest.stream());
	}

	@Test
	void testDeserializationWithNullExtraBody() throws Exception {
		// Arrange: JSON without any extra fields (standard OpenAI request)
		String json = """
				{
					"model": "gpt-4",
					"messages": [],
					"stream": false,
					"temperature": 0.7
				}
				""";

		// Act: Deserialize
		ChatCompletionRequest request = JsonMapper.shared().readValue(json, ChatCompletionRequest.class);

		// Assert: extraBody should be null or empty when no extra fields present
		// (depending on Jackson configuration and constructor behavior)
		if (request.extraBody() != null) {
			assertThat(request.extraBody()).isEmpty();
		}

		// Assert: Standard fields should work
		assertThat(request.model()).isEqualTo("gpt-4");
		assertThat(request.temperature()).isEqualTo(0.7);
	}

	@Test
	void testDeserializationWithComplexExtraFields() throws Exception {
		// Arrange: JSON with real vLLM extra fields (complex types)
		String json = """
				{
					"model": "deepseek-r1",
					"messages": [],
					"stream": false,
					"top_k": 50,
					"min_p": 0.05,
					"best_of": 3,
					"guided_json": "{\\"type\\": \\"object\\", \\"properties\\": {\\"name\\": {\\"type\\": \\"string\\"}}}",
					"stop_token_ids": [128001, 128009],
					"skip_special_tokens": true
				}
				""";

		// Act: Deserialize
		ChatCompletionRequest request = JsonMapper.shared().readValue(json, ChatCompletionRequest.class);

		// Assert: Real vLLM extra fields should be captured
		assertThat(request.extraBody()).isNotNull();
		assertThat(request.extraBody()).containsEntry("top_k", 50);
		assertThat(request.extraBody()).containsEntry("min_p", 0.05);
		assertThat(request.extraBody()).containsEntry("best_of", 3);
		assertThat(request.extraBody()).containsKey("guided_json");
		assertThat(request.extraBody()).containsKey("stop_token_ids");
		assertThat(request.extraBody()).containsEntry("skip_special_tokens", true);

		// Assert: Complex types should be preserved as String/List
		assertThat(request.extraBody().get("guided_json")).isInstanceOf(String.class);
		assertThat(request.extraBody().get("stop_token_ids")).isInstanceOf(List.class);
	}

	@Test
	void testMergeWithExtraBody() throws Exception {
		// Arrange: Create OpenAiChatOptions with extraBody
		OpenAiChatOptions requestOptions = OpenAiChatOptions.builder()
			.model("test-model")
			.extraBody(Map.of("enable_thinking", true, "max_depth", 10))
			.build();

		// Create empty ChatCompletionRequest
		ChatCompletionRequest request = new ChatCompletionRequest(null, null);

		// Act: Merge options into request
		request = ModelOptionsUtils.merge(requestOptions, request, ChatCompletionRequest.class);

		// Assert: Verify extraBody was successfully merged
		assertThat(request.extraBody()).isNotNull();
		assertThat(request.extraBody()).containsEntry("enable_thinking", true);
		assertThat(request.extraBody()).containsEntry("max_depth", 10);
		assertThat(request.model()).isEqualTo("test-model");
	}

}
