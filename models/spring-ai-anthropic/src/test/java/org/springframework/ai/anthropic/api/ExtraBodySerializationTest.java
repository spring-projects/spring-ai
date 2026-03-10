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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi.AnthropicMessage;
import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest;
import org.springframework.ai.anthropic.api.AnthropicApi.ContentBlock;
import org.springframework.ai.anthropic.api.AnthropicApi.Role;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for extraBody serialization/deserialization in
 * {@link AnthropicApi.ChatCompletionRequest}.
 *
 * @author Spring AI
 * @since 2.0.0
 */
class ExtraBodySerializationTest {

	@Test
	void shouldFlattenExtraBodyToTopLevel() throws Exception {
		List<AnthropicMessage> messages = List.of(new AnthropicMessage(List.of(new ContentBlock("Hello")), Role.USER));

		ChatCompletionRequest request = new ChatCompletionRequest("claude-opus-4-6", messages, null, 1024, null, null,
				false, null, null, null, null, null, null, null, null, null, Map.of("custom_param", "value"));

		String json = JsonMapper.shared().writeValueAsString(request);

		// extraBody should be flattened - custom_param should be at top level
		assertThat(json).contains("\"custom_param\":\"value\"");
		// extra_body key should NOT appear in the JSON
		assertThat(json).doesNotContain("\"extra_body\"");
	}

	@Test
	void shouldHandleEmptyExtraBody() throws Exception {
		List<AnthropicMessage> messages = List.of(new AnthropicMessage(List.of(new ContentBlock("Hello")), Role.USER));

		ChatCompletionRequest request = new ChatCompletionRequest("claude-opus-4-6", messages, null, 1024, null, null);

		String json = JsonMapper.shared().writeValueAsString(request);

		assertThat(json).doesNotContain("\"extra_body\"");
	}

	@Test
	void shouldCaptureUnknownPropertiesDuringDeserialization() throws Exception {
		String json = """
				{
					"model": "claude-opus-4-6",
					"messages": [{"role": "user", "content": [{"type": "text", "text": "Hello"}]}],
					"max_tokens": 1024,
					"custom_field": "custom_value",
					"another_field": 42
				}
				""";

		ChatCompletionRequest request = JsonMapper.shared().readValue(json, ChatCompletionRequest.class);

		assertThat(request.model()).isEqualTo("claude-opus-4-6");
		assertThat(request.extraBody()).containsEntry("custom_field", "custom_value");
		assertThat(request.extraBody()).containsEntry("another_field", 42);
	}

	@Test
	void shouldRoundTripSerializeAndDeserialize() throws Exception {
		List<AnthropicMessage> messages = List.of(new AnthropicMessage(List.of(new ContentBlock("Hello")), Role.USER));

		ChatCompletionRequest original = new ChatCompletionRequest("claude-opus-4-6", messages, null, 1024, null, null,
				false, null, null, null, null, null, null, null, null, null, Map.of("custom_param", "test_value"));

		String json = JsonMapper.shared().writeValueAsString(original);
		ChatCompletionRequest deserialized = JsonMapper.shared().readValue(json, ChatCompletionRequest.class);

		assertThat(deserialized.model()).isEqualTo("claude-opus-4-6");
		assertThat(deserialized.extraBody()).containsEntry("custom_param", "test_value");
	}

	@Test
	void shouldMergeExtraBodyViaModelOptionsUtils() {
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model("claude-opus-4-6")
			.maxTokens(1024)
			.extraBody(Map.of("custom_param", "value"))
			.build();

		assertThat(options.getExtraBody()).containsEntry("custom_param", "value");
	}

	@Test
	void shouldSerializeSpeedField() throws Exception {
		List<AnthropicMessage> messages = List.of(new AnthropicMessage(List.of(new ContentBlock("Hello")), Role.USER));

		ChatCompletionRequest request = new ChatCompletionRequest("claude-opus-4-6", messages, null, 1024, null, null,
				false, null, null, null, null, null, null, null, null, "fast", null);

		String json = JsonMapper.shared().writeValueAsString(request);

		assertThat(json).contains("\"speed\":\"fast\"");
	}

}
