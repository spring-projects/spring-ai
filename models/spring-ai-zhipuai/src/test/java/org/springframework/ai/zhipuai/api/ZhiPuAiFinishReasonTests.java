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

package org.springframework.ai.zhipuai.api;

import org.junit.jupiter.api.Test;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletion;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletionChunk;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletionFinishReason;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for Zhipu AI finish_reason deserialization, covering both standard values and
 * Zhipu-specific values such as {@code network_error} and {@code sensitive}.
 *
 * <p>
 * See <a href="https://github.com/spring-projects/spring-ai/issues/5530">GH-5530</a> for
 * background.
 *
 * @author Zexuan Peng
 */
class ZhiPuAiFinishReasonTests {

	// I. ChatCompletion (non-streaming) deserialization

	@Test
	void deserializeChatCompletionWithNetworkErrorFinishReason() {
		String json = """
				{
					"id": "202603031103252ba2e084c0074fc3",
					"created": 1772507037,
					"model": "GLM-5",
					"choices": [{
						"index": 0,
						"finish_reason": "network_error",
						"message": {"role": "assistant", "content": ""}
					}],
					"usage": {"prompt_tokens": 10, "completion_tokens": 0, "total_tokens": 10}
				}
				""";

		ChatCompletion completion = ModelOptionsUtils.jsonToObject(json, ChatCompletion.class);

		assertThat(completion).isNotNull();
		assertThat(completion.choices()).hasSize(1);
		assertThat(completion.choices().get(0).finishReason()).isEqualTo(ChatCompletionFinishReason.NETWORK_ERROR);
	}

	@Test
	void deserializeChatCompletionWithSensitiveFinishReason() {
		String json = """
				{
					"id": "test-id",
					"created": 1772507037,
					"model": "GLM-5",
					"choices": [{
						"index": 0,
						"finish_reason": "sensitive",
						"message": {"role": "assistant", "content": ""}
					}],
					"usage": {"prompt_tokens": 10, "completion_tokens": 0, "total_tokens": 10}
				}
				""";

		ChatCompletion completion = ModelOptionsUtils.jsonToObject(json, ChatCompletion.class);

		assertThat(completion).isNotNull();
		assertThat(completion.choices()).hasSize(1);
		assertThat(completion.choices().get(0).finishReason()).isEqualTo(ChatCompletionFinishReason.SENSITIVE);
	}

	@Test
	void deserializeChatCompletionWithStopFinishReason() {
		String json = """
				{
					"id": "test-id",
					"created": 1772507037,
					"model": "GLM-5",
					"choices": [{
						"index": 0,
						"finish_reason": "stop",
						"message": {"role": "assistant", "content": "Hello"}
					}],
					"usage": {"prompt_tokens": 5, "completion_tokens": 2, "total_tokens": 7}
				}
				""";

		ChatCompletion completion = ModelOptionsUtils.jsonToObject(json, ChatCompletion.class);

		assertThat(completion).isNotNull();
		assertThat(completion.choices().get(0).finishReason()).isEqualTo(ChatCompletionFinishReason.STOP);
	}

	@Test
	void deserializeChatCompletionWithNullFinishReason() {
		String json = """
				{
					"id": "test-id",
					"created": 1772507037,
					"model": "GLM-5",
					"choices": [{
						"index": 0,
						"finish_reason": null,
						"message": {"role": "assistant", "content": "Hello"}
					}],
					"usage": {"prompt_tokens": 5, "completion_tokens": 2, "total_tokens": 7}
				}
				""";

		ChatCompletion completion = ModelOptionsUtils.jsonToObject(json, ChatCompletion.class);

		assertThat(completion).isNotNull();
		assertThat(completion.choices().get(0).finishReason()).isNull();
	}

	@Test
	void deserializeChatCompletionWithEmptyStringFinishReason() {
		String json = """
				{
					"id": "test-id",
					"created": 1772507037,
					"model": "GLM-5",
					"choices": [{
						"index": 0,
						"finish_reason": "",
						"message": {"role": "assistant", "content": ""}
					}],
					"usage": {"prompt_tokens": 5, "completion_tokens": 0, "total_tokens": 5}
				}
				""";

		ChatCompletion completion = ModelOptionsUtils.jsonToObject(json, ChatCompletion.class);

		assertThat(completion).isNotNull();
		assertThat(completion.choices().get(0).finishReason()).isEqualTo(ChatCompletionFinishReason.UNKNOWN);
	}

	// II. ChatCompletionChunk (streaming) deserialization

	@Test
	void deserializeChunkWithNetworkErrorFinishReason() {
		String json = """
				{
					"id": "202603031103252ba2e084c0074fc3",
					"created": 1772507037,
					"model": "GLM-5",
					"choices": [{
						"index": 0,
						"finish_reason": "network_error",
						"delta": {"role": "assistant", "content": ""}
					}]
				}
				""";

		ChatCompletionChunk chunk = ModelOptionsUtils.jsonToObject(json, ChatCompletionChunk.class);

		assertThat(chunk).isNotNull();
		assertThat(chunk.choices()).hasSize(1);
		assertThat(chunk.choices().get(0).finishReason()).isEqualTo(ChatCompletionFinishReason.NETWORK_ERROR);
	}

	@Test
	void deserializeChunkWithSensitiveFinishReason() {
		String json = """
				{
					"id": "test-id",
					"created": 1772507037,
					"model": "GLM-5",
					"choices": [{
						"index": 0,
						"finish_reason": "sensitive",
						"delta": {"role": "assistant", "content": ""}
					}]
				}
				""";

		ChatCompletionChunk chunk = ModelOptionsUtils.jsonToObject(json, ChatCompletionChunk.class);

		assertThat(chunk).isNotNull();
		assertThat(chunk.choices().get(0).finishReason()).isEqualTo(ChatCompletionFinishReason.SENSITIVE);
	}

	@Test
	void deserializeChunkWithNullFinishReason() {
		// Null finish_reason is expected during streaming while the model is still
		// generating tokens.
		String json = """
				{
					"id": "test-id",
					"created": 1772507037,
					"model": "GLM-5",
					"choices": [{
						"index": 0,
						"finish_reason": null,
						"delta": {"role": "assistant", "content": "Hello"}
					}]
				}
				""";

		ChatCompletionChunk chunk = ModelOptionsUtils.jsonToObject(json, ChatCompletionChunk.class);

		assertThat(chunk).isNotNull();
		assertThat(chunk.choices().get(0).finishReason()).isNull();
	}

	@Test
	void deserializeChunkWithEmptyStringFinishReason() {
		String json = """
				{
					"id": "test-id",
					"created": 1772507037,
					"model": "GLM-5",
					"choices": [{
						"index": 0,
						"finish_reason": "",
						"delta": {"role": "assistant", "content": ""}
					}]
				}
				""";

		ChatCompletionChunk chunk = ModelOptionsUtils.jsonToObject(json, ChatCompletionChunk.class);

		assertThat(chunk).isNotNull();
		assertThat(chunk.choices().get(0).finishReason()).isEqualTo(ChatCompletionFinishReason.UNKNOWN);
	}

	// III. Direct enum deserialization

	@Test
	void deserializeAllStandardEnumValues() {
		assertThat(deserialize("\"stop\"")).isEqualTo(ChatCompletionFinishReason.STOP);
		assertThat(deserialize("\"length\"")).isEqualTo(ChatCompletionFinishReason.LENGTH);
		assertThat(deserialize("\"content_filter\"")).isEqualTo(ChatCompletionFinishReason.CONTENT_FILTER);
		assertThat(deserialize("\"tool_calls\"")).isEqualTo(ChatCompletionFinishReason.TOOL_CALLS);
		assertThat(deserialize("\"tool_call\"")).isEqualTo(ChatCompletionFinishReason.TOOL_CALL);
	}

	@Test
	void deserializeZhipuSpecificEnumValues() {
		assertThat(deserialize("\"network_error\"")).isEqualTo(ChatCompletionFinishReason.NETWORK_ERROR);
		assertThat(deserialize("\"sensitive\"")).isEqualTo(ChatCompletionFinishReason.SENSITIVE);
	}

	/**
	 * Helper: deserialize a single JSON string value as
	 * {@link ChatCompletionFinishReason} by embedding it in a minimal
	 * {@link ChatCompletion} payload.
	 */
	private ChatCompletionFinishReason deserialize(String finishReasonJson) {
		String json = """
				{
					"id": "test",
					"created": 1,
					"model": "test",
					"choices": [{
						"index": 0,
						"finish_reason": %s,
						"message": {"role": "assistant", "content": ""}
					}]
				}
				""".formatted(finishReasonJson);
		ChatCompletion completion = ModelOptionsUtils.jsonToObject(json, ChatCompletion.class);
		return completion.choices().get(0).finishReason();
	}

}
