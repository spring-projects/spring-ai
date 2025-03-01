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

package org.springframework.ai.anthropic;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.springframework.ai.anthropic.api.AnthropicApi.ChatCompletionRequest.Metadata;

/**
 * Tests for {@link AnthropicChatOptions}.
 *
 * @author Alexandros Pappas
 */
class AnthropicChatOptionsTests {

	@Test
	void testBuilderWithAllFields() {
		AnthropicChatOptions options = AnthropicChatOptions.builder()
			.model("test-model")
			.maxTokens(100)
			.stopSequences(List.of("stop1", "stop2"))
			.temperature(0.7)
			.topP(0.8)
			.topK(50)
			.metadata(new Metadata("userId_123"))
			.build();

		assertThat(options).extracting("model", "maxTokens", "stopSequences", "temperature", "topP", "topK", "metadata")
			.containsExactly("test-model", 100, List.of("stop1", "stop2"), 0.7, 0.8, 50, new Metadata("userId_123"));
	}

	@Test
	void testCopy() {
		AnthropicChatOptions original = AnthropicChatOptions.builder()
			.model("test-model")
			.maxTokens(100)
			.stopSequences(List.of("stop1", "stop2"))
			.temperature(0.7)
			.topP(0.8)
			.topK(50)
			.metadata(new Metadata("userId_123"))
			.toolContext(Map.of("key1", "value1"))
			.build();

		AnthropicChatOptions copied = original.copy();

		assertThat(copied).isNotSameAs(original).isEqualTo(original);
		// Ensure deep copy
		assertThat(copied.getStopSequences()).isNotSameAs(original.getStopSequences());
		assertThat(copied.getToolContext()).isNotSameAs(original.getToolContext());
	}

	@Test
	void testSetters() {
		AnthropicChatOptions options = new AnthropicChatOptions();
		options.setModel("test-model");
		options.setMaxTokens(100);
		options.setTemperature(0.7);
		options.setTopK(50);
		options.setTopP(0.8);
		options.setStopSequences(List.of("stop1", "stop2"));
		options.setMetadata(new Metadata("userId_123"));

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getMaxTokens()).isEqualTo(100);
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getTopK()).isEqualTo(50);
		assertThat(options.getTopP()).isEqualTo(0.8);
		assertThat(options.getStopSequences()).isEqualTo(List.of("stop1", "stop2"));
		assertThat(options.getMetadata()).isEqualTo(new Metadata("userId_123"));
	}

	@Test
	void testDefaultValues() {
		AnthropicChatOptions options = new AnthropicChatOptions();
		assertThat(options.getModel()).isNull();
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getTopK()).isNull();
		assertThat(options.getTopP()).isNull();
		assertThat(options.getStopSequences()).isNull();
		assertThat(options.getMetadata()).isNull();
	}

}
