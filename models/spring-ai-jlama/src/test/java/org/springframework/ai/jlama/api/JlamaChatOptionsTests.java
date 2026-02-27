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

package org.springframework.ai.jlama.api;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JlamaChatOptions}.
 *
 * @author chabinhwang
 */
class JlamaChatOptionsTests {

	@Test
	void testBuilderWithAllFields() {
		var options = JlamaChatOptions.builder()
			.model("test-model")
			.temperature(0.8)
			.maxTokens(512)
			.topK(50)
			.topP(0.95)
			.frequencyPenalty(0.5)
			.presencePenalty(0.6)
			.stopSequences(List.of("stop1", "stop2"))
			.seed(42L)
			.build();

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getTemperature()).isEqualTo(0.8);
		assertThat(options.getMaxTokens()).isEqualTo(512);
		assertThat(options.getTopK()).isEqualTo(50);
		assertThat(options.getTopP()).isEqualTo(0.95);
		assertThat(options.getFrequencyPenalty()).isEqualTo(0.5);
		assertThat(options.getPresencePenalty()).isEqualTo(0.6);
		assertThat(options.getStopSequences()).containsExactly("stop1", "stop2");
		assertThat(options.getSeed()).isEqualTo(42L);
	}

	@Test
	void testCopy() {
		var original = JlamaChatOptions.builder()
			.model("original-model")
			.temperature(0.7)
			.stopSequences(List.of("stop1", "stop2"))
			.build();

		JlamaChatOptions copy = original.copy();

		assertThat(copy).isNotSameAs(original);
		assertThat(copy.getModel()).isEqualTo(original.getModel());
		assertThat(copy.getTemperature()).isEqualTo(original.getTemperature());
		assertThat(copy.getStopSequences()).isEqualTo(original.getStopSequences());
		assertThat(copy.getStopSequences()).isNotSameAs(original.getStopSequences());
	}

	@Test
	void testCopyPreservesAllFields() {
		var original = JlamaChatOptions.builder()
			.model("test-model")
			.temperature(0.8)
			.maxTokens(512)
			.topK(50)
			.topP(0.95)
			.frequencyPenalty(0.5)
			.presencePenalty(0.6)
			.stopSequences(List.of("stop1", "stop2"))
			.seed(42L)
			.build();

		JlamaChatOptions copy = original.copy();

		assertThat(copy.getModel()).isEqualTo(original.getModel());
		assertThat(copy.getTemperature()).isEqualTo(original.getTemperature());
		assertThat(copy.getMaxTokens()).isEqualTo(original.getMaxTokens());
		assertThat(copy.getTopK()).isEqualTo(original.getTopK());
		assertThat(copy.getTopP()).isEqualTo(original.getTopP());
		assertThat(copy.getFrequencyPenalty()).isEqualTo(original.getFrequencyPenalty());
		assertThat(copy.getPresencePenalty()).isEqualTo(original.getPresencePenalty());
		assertThat(copy.getStopSequences()).isEqualTo(original.getStopSequences());
		assertThat(copy.getSeed()).isEqualTo(original.getSeed());
	}

	@Test
	void testEqualsAndHashCode() {
		var options1 = JlamaChatOptions.builder().model("test-model").temperature(0.7).maxTokens(512).build();

		var options2 = JlamaChatOptions.builder().model("test-model").temperature(0.7).maxTokens(512).build();

		assertThat(options1).isEqualTo(options2);
		assertThat(options1.hashCode()).isEqualTo(options2.hashCode());
	}

	@Test
	void testNotEquals() {
		var options1 = JlamaChatOptions.builder().model("model1").temperature(0.7).build();

		var options2 = JlamaChatOptions.builder().model("model2").temperature(0.7).build();

		assertThat(options1).isNotEqualTo(options2);
	}

	@Test
	void testSettersWithNullValues() {
		var options = new JlamaChatOptions();
		options.setModel(null);
		options.setTemperature(null);
		options.setMaxTokens(null);
		options.setTopK(null);
		options.setTopP(null);
		options.setFrequencyPenalty(null);
		options.setPresencePenalty(null);
		options.setStopSequences(null);
		options.setSeed(null);

		assertThat(options.getModel()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getTopK()).isNull();
		assertThat(options.getTopP()).isNull();
		assertThat(options.getFrequencyPenalty()).isNull();
		assertThat(options.getPresencePenalty()).isNull();
		assertThat(options.getStopSequences()).isNull();
		assertThat(options.getSeed()).isNull();
	}

	@Test
	void testSetters() {
		var options = new JlamaChatOptions();
		options.setModel("test-model");
		options.setTemperature(0.8);
		options.setMaxTokens(512);
		options.setTopK(50);
		options.setTopP(0.95);
		options.setFrequencyPenalty(0.5);
		options.setPresencePenalty(0.6);
		options.setStopSequences(List.of("stop1"));
		options.setSeed(42L);

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getTemperature()).isEqualTo(0.8);
		assertThat(options.getMaxTokens()).isEqualTo(512);
		assertThat(options.getTopK()).isEqualTo(50);
		assertThat(options.getTopP()).isEqualTo(0.95);
		assertThat(options.getFrequencyPenalty()).isEqualTo(0.5);
		assertThat(options.getPresencePenalty()).isEqualTo(0.6);
		assertThat(options.getStopSequences()).containsExactly("stop1");
		assertThat(options.getSeed()).isEqualTo(42L);
	}

	@Test
	void testDefaultValues() {
		var options = new JlamaChatOptions();

		assertThat(options.getModel()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getTopK()).isNull();
		assertThat(options.getTopP()).isNull();
		assertThat(options.getFrequencyPenalty()).isNull();
		assertThat(options.getPresencePenalty()).isNull();
		assertThat(options.getStopSequences()).isNull();
		assertThat(options.getSeed()).isNull();
	}

	@Test
	void testBuilderPartialConfiguration() {
		var options = JlamaChatOptions.builder().model("test-model").temperature(0.7).build();

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getTopK()).isNull();
		assertThat(options.getTopP()).isNull();
	}

	@Test
	void testCopyMutationDoesNotAffectOriginal() {
		var original = JlamaChatOptions.builder()
			.model("original-model")
			.stopSequences(List.of("stop1", "stop2"))
			.build();

		JlamaChatOptions copy = original.copy();
		copy.setModel("modified-model");
		copy.getStopSequences().add("stop3");

		assertThat(original.getModel()).isEqualTo("original-model");
		assertThat(original.getStopSequences()).containsExactly("stop1", "stop2");
		assertThat(copy.getModel()).isEqualTo("modified-model");
		assertThat(copy.getStopSequences()).containsExactly("stop1", "stop2", "stop3");
	}

}
