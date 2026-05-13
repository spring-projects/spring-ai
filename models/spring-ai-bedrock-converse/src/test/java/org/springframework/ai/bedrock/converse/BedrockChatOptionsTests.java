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

package org.springframework.ai.bedrock.converse;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.bedrock.converse.BedrockChatOptions.Builder;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.ai.test.options.AbstractChatOptionsTests;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BedrockChatOptions}.
 *
 * @author Sun Yuhan
 * @author Sebastien Deleuze
 */
class BedrockChatOptionsTests extends AbstractChatOptionsTests<BedrockChatOptions, Builder> {

	@Override
	protected Class<BedrockChatOptions> getConcreteOptionsClass() {
		return BedrockChatOptions.class;
	}

	@Override
	protected Builder readyToBuildBuilder() {
		return BedrockChatOptions.builder();
	}

	@Test
	void testBuilderWithAllFields() {
		BedrockChatOptions options = BedrockChatOptions.builder()
			.model("test-model")
			.frequencyPenalty(0.0)
			.maxTokens(100)
			.presencePenalty(0.0)
			.requestParameters(Map.of("requestId", "1234"))
			.stopSequences(List.of("stop1", "stop2"))
			.temperature(0.7)
			.topP(0.8)
			.topK(50)
			.outputSchema("{\"type\":\"object\"}")
			.build();

		assertThat(options)
			.extracting("model", "frequencyPenalty", "maxTokens", "presencePenalty", "requestParameters",
					"stopSequences", "temperature", "topP", "topK")
			.containsExactly("test-model", 0.0, 100, 0.0, Map.of("requestId", "1234"), List.of("stop1", "stop2"), 0.7,
					0.8, 50);
		assertThat(options.getOutputSchema()).isEqualTo("{\"type\":\"object\"}");
	}

	@Test
	void testCopy() {
		BedrockChatOptions original = BedrockChatOptions.builder()
			.model("test-model")
			.frequencyPenalty(0.0)
			.maxTokens(100)
			.presencePenalty(0.0)
			.stopSequences(List.of("stop1", "stop2"))
			.temperature(0.7)
			.topP(0.8)
			.topK(50)
			.toolContext(Map.of("key1", "value1"))
			.outputSchema("{\"type\":\"object\"}")
			.build();

		BedrockChatOptions copied = original.copy();

		assertThat(copied).isNotSameAs(original).isEqualTo(original);
		// Ensure deep copy
		assertThat(copied.getStopSequences()).isNotSameAs(original.getStopSequences());
		assertThat(copied.getToolContext()).isNotSameAs(original.getToolContext());
		assertThat(copied.getOutputSchema()).isEqualTo(original.getOutputSchema());
	}

	@Test
	void testDefaultValues() {
		BedrockChatOptions options = new BedrockChatOptions();
		assertThat(options.getModel()).isNull();
		assertThat(options.getFrequencyPenalty()).isNull();
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getPresencePenalty()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getTopK()).isNull();
		assertThat(options.getTopP()).isNull();
		assertThat(options.getStopSequences()).isNull();
		assertThat(options.getOutputSchema()).isNull();
	}

	@Test
	void testImplementsStructuredOutputChatOptions() {
		BedrockChatOptions options = new BedrockChatOptions();

		assertThat(options).isInstanceOf(StructuredOutputChatOptions.class);
	}

}
