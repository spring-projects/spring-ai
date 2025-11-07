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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify JSON serialization behavior of extraBody parameter. This test verifies
 * that @JsonAnyGetter correctly flattens extraBody fields to the top level of the JSON
 * request, matching the behavior expected by OpenAI-compatible servers like vLLM, Ollama,
 * and matching the pattern used by the official OpenAI SDK and LangChain4j.
 */
class ExtraBodySerializationTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

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
		String json = this.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);

		// Debug: Print the actual JSON
		System.out.println("=== JSON Output (with @JsonAnyGetter) ===");
		System.out.println(json);

		// Assert: Verify @JsonAnyGetter flattens fields to top level
		assertThat(json).contains("\"top_k\" : 50");
		assertThat(json).contains("\"repetition_penalty\" : 1.1");
		assertThat(json).doesNotContain("\"extra_body\"");

		System.out.println("\n=== Analysis ===");
		System.out.println("✓ Fields are FLATTENED to top level (correct!)");
		System.out.println("  Format: { \"model\": \"gpt-4\", \"top_k\": 50, \"repetition_penalty\": 1.1 }");
		System.out.println("  This matches official OpenAI SDK and LangChain4j behavior");
		System.out.println("  This is CORRECT for vLLM, Ollama, and other OpenAI-compatible servers");
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
		String json = this.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);

		// Debug
		System.out.println("\n=== JSON Output (empty extraBody map) ===");
		System.out.println(json);

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
		String json = this.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);

		// Debug
		System.out.println("\n=== JSON Output (null extraBody) ===");
		System.out.println(json);

		// Assert: extra_body should not appear in JSON when null
		assertThat(json).doesNotContain("extra_body");
		assertThat(json).doesNotContain("top_k");
	}

}
