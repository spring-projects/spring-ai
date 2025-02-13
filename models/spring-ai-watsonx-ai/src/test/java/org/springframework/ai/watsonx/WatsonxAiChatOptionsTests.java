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

package org.springframework.ai.watsonx;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link WatsonxAiChatOptions}.
 *
 * @author Alexandros Pappas
 */
class WatsonxAiChatOptionsTests {

	@Test
	void testBuilderWithAllFields() {
		WatsonxAiChatOptions options = WatsonxAiChatOptions.builder()
			.temperature(0.6)
			.topP(0.6)
			.topK(50)
			.decodingMethod("greedy")
			.maxNewTokens(100)
			.minNewTokens(10)
			.stopSequences(List.of("test"))
			.repetitionPenalty(1.2)
			.randomSeed(42)
			.model("test-model")
			.additionalProperties(Map.of("key1", "value1"))
			.build();

		assertThat(options)
			.extracting("temperature", "topP", "topK", "decodingMethod", "maxNewTokens", "minNewTokens",
					"stopSequences", "repetitionPenalty", "randomSeed", "model", "additionalProperties")
			.containsExactly(0.6, 0.6, 50, "greedy", 100, 10, List.of("test"), 1.2, 42, "test-model",
					Map.of("key1", "value1"));
	}

	@Test
	void testCopy() {
		WatsonxAiChatOptions original = WatsonxAiChatOptions.builder()
			.temperature(0.6)
			.topP(0.6)
			.topK(50)
			.decodingMethod("greedy")
			.maxNewTokens(100)
			.minNewTokens(10)
			.stopSequences(List.of("test"))
			.repetitionPenalty(1.2)
			.randomSeed(42)
			.model("test-model")
			.additionalProperties(Map.of("key1", "value1"))
			.build();

		WatsonxAiChatOptions copied = original.copy();

		assertThat(copied).isNotSameAs(original).isEqualTo(original);
		assertThat(copied.getStopSequences()).isNotSameAs(original.getStopSequences());
		assertThat(copied.getAdditionalProperties()).isNotSameAs(original.getAdditionalProperties());
	}

	@Test
	void testSetters() {
		WatsonxAiChatOptions options = new WatsonxAiChatOptions();
		options.setTemperature(0.6);
		options.setTopP(0.6);
		options.setTopK(50);
		options.setDecodingMethod("greedy");
		options.setMaxNewTokens(100);
		options.setMinNewTokens(10);
		options.setStopSequences(List.of("test"));
		options.setRepetitionPenalty(1.2);
		options.setRandomSeed(42);
		options.setModel("test-model");
		options.addAdditionalProperty("key1", "value1");

		assertThat(options.getTemperature()).isEqualTo(0.6);
		assertThat(options.getTopP()).isEqualTo(0.6);
		assertThat(options.getTopK()).isEqualTo(50);
		assertThat(options.getDecodingMethod()).isEqualTo("greedy");
		assertThat(options.getMaxNewTokens()).isEqualTo(100);
		assertThat(options.getMinNewTokens()).isEqualTo(10);
		assertThat(options.getStopSequences()).isEqualTo(List.of("test"));
		assertThat(options.getRepetitionPenalty()).isEqualTo(1.2);
		assertThat(options.getRandomSeed()).isEqualTo(42);
		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getAdditionalProperties()).isEqualTo(Map.of("key1", "value1"));
	}

	@Test
	void testDefaultValues() {
		WatsonxAiChatOptions options = new WatsonxAiChatOptions();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getTopP()).isNull();
		assertThat(options.getTopK()).isNull();
		assertThat(options.getDecodingMethod()).isNull();
		assertThat(options.getMaxNewTokens()).isNull();
		assertThat(options.getMinNewTokens()).isNull();
		assertThat(options.getStopSequences()).isNull();
		assertThat(options.getRepetitionPenalty()).isNull();
		assertThat(options.getRandomSeed()).isNull();
		assertThat(options.getModel()).isNull();
		assertThat(options.getAdditionalProperties()).isEqualTo(new HashMap<>());
	}

}
