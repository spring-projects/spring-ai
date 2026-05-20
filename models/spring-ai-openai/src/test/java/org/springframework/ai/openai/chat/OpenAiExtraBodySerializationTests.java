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

package org.springframework.ai.openai.chat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.openai.OpenAiChatOptions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify JSON serialization behavior of extraBody parameter in the SDK Options.
 * This test verifies that @JsonAnyGetter correctly flattens extraBody fields to the top
 * level of the JSON request.
 *
 * @author Ilayaperumal Gopinathan
 */
class OpenAiExtraBodySerializationTests {

	@Test
	void testExtraBodySerializationFlattensToTopLevel() throws Exception {
		// Arrange: Create request with extraBody containing parameters
		OpenAiChatOptions options = OpenAiChatOptions.builder()
			.model("gpt-4")
			.extraBody(Map.of("top_k", 50, "repetition_penalty", 1.1))
			.build();

		// Act: Serialize to JSON
		String json = JsonMapper.shared().writerWithDefaultPrettyPrinter().writeValueAsString(options);

		// Assert: Verify @JsonAnyGetter flattens fields to top level
		assertThat(json).contains("\"top_k\" : 50");
		assertThat(json).contains("\"repetition_penalty\" : 1.1");
		assertThat(json).doesNotContain("\"extra_body\"");
	}

	@Test
	void testExtraBodyWithEmptyMap() throws Exception {
		// Arrange: Request with empty extraBody map
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("gpt-4").extraBody(Map.of()).build();

		// Act
		String json = JsonMapper.shared().writerWithDefaultPrettyPrinter().writeValueAsString(options);

		// Assert: No extra fields should appear
		assertThat(json).doesNotContain("extra_body");
		assertThat(json).doesNotContain("top_k");
	}

	@Test
	void testExtraBodyWithNull() throws Exception {
		// Arrange: Request with null extraBody
		OpenAiChatOptions options = OpenAiChatOptions.builder().model("gpt-4").extraBody(null).build();

		// Act
		String json = JsonMapper.shared().writerWithDefaultPrettyPrinter().writeValueAsString(options);

		// Assert: No extra fields should appear
		assertThat(json).doesNotContain("extra_body");
	}

	@Test
	void testMergeWithExtraBody() {
		// Arrange: Create options with extraBody
		OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder()
			.model("test-model")
			.extraBody(Map.of("enable_thinking", true, "max_depth", 10))
			.build();

		OpenAiChatOptions.Builder runtimeOptions = OpenAiChatOptions.builder()
			.temperature(0.9)
			.extraBody(Map.of("enable_thinking", false, "top_k", 50));

		// Act: Merge options using the builder's combineWith method, which is the actual
		// mechanism used by OpenAiChatModel
		OpenAiChatOptions merged = defaultOptions.mutate().combineWith(runtimeOptions).build();

		// Assert: Verify extraBody was successfully merged
		assertThat(merged.getExtraBody()).isNotNull();
		// runtime option overrides default option for same key
		assertThat(merged.getExtraBody()).containsEntry("enable_thinking", false);
		assertThat(merged.getExtraBody()).containsEntry("max_depth", 10);
		assertThat(merged.getExtraBody()).containsEntry("top_k", 50);
		assertThat(merged.getModel()).isEqualTo("test-model");
		assertThat(merged.getTemperature()).isEqualTo(0.9);
	}

}
