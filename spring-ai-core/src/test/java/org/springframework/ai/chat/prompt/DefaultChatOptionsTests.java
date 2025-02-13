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

package org.springframework.ai.chat.prompt;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DefaultChatOptions}.
 *
 * @author Alexandros Pappas
 */
class DefaultChatOptionsTests {

	@Test
	void testBuilderWithAllFields() {
		DefaultChatOptions options = DefaultChatOptions.builder()
			.model("test-model")
			.frequencyPenalty(0.5)
			.maxTokens(100)
			.presencePenalty(0.6)
			.stopSequences(List.of("stop1", "stop2"))
			.temperature(0.7)
			.topK(50)
			.topP(0.8)
			.build();

		assertThat(options)
			.extracting("model", "frequencyPenalty", "maxTokens", "presencePenalty", "stopSequences", "temperature",
					"topK", "topP")
			.containsExactly("test-model", 0.5, 100, 0.6, List.of("stop1", "stop2"), 0.7, 50, 0.8);
	}

	@Test
	void testCopy() {
		DefaultChatOptions original = DefaultChatOptions.builder()
			.model("test-model")
			.frequencyPenalty(0.5)
			.maxTokens(100)
			.presencePenalty(0.6)
			.stopSequences(List.of("stop1", "stop2"))
			.temperature(0.7)
			.topK(50)
			.topP(0.8)
			.build();

		DefaultChatOptions copied = original.copy();

		assertThat(copied).isNotSameAs(original).isEqualTo(original);
		// Ensure deep copy
		assertThat(copied.getStopSequences()).isNotSameAs(original.getStopSequences());
	}

	@Test
	void testSetters() {
		DefaultChatOptions options = new DefaultChatOptions();
		options.setModel("test-model");
		options.setFrequencyPenalty(0.5);
		options.setMaxTokens(100);
		options.setPresencePenalty(0.6);
		options.setStopSequences(List.of("stop1", "stop2"));
		options.setTemperature(0.7);
		options.setTopK(50);
		options.setTopP(0.8);

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getFrequencyPenalty()).isEqualTo(0.5);
		assertThat(options.getMaxTokens()).isEqualTo(100);
		assertThat(options.getPresencePenalty()).isEqualTo(0.6);
		assertThat(options.getStopSequences()).isEqualTo(List.of("stop1", "stop2"));
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getTopK()).isEqualTo(50);
		assertThat(options.getTopP()).isEqualTo(0.8);
	}

	@Test
	void testDefaultValues() {
		DefaultChatOptions options = new DefaultChatOptions();
		assertThat(options.getModel()).isNull();
		assertThat(options.getFrequencyPenalty()).isNull();
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getPresencePenalty()).isNull();
		assertThat(options.getStopSequences()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getTopK()).isNull();
		assertThat(options.getTopP()).isNull();
	}

}
