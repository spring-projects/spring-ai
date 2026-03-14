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

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.ai.test.options.AbstractChatOptionsTests;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BedrockChatOptions}.
 *
 * @author Sun Yuhan
 */
class BedrockChatOptionsTests<B extends BedrockChatOptions.Builder<B>>
		extends AbstractChatOptionsTests<BedrockChatOptions, B> {

	@Override
	protected Class<BedrockChatOptions> getConcreteOptionsClass() {
		return BedrockChatOptions.class;
	}

	@Override
	@SuppressWarnings("unchecked")
	protected B readyToBuildBuilder() {
		return (B) BedrockChatOptions.builder();
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
	void testSetters() {
		BedrockChatOptions options = new BedrockChatOptions();
		options.setModel("test-model");
		options.setFrequencyPenalty(0.0);
		options.setMaxTokens(100);
		options.setPresencePenalty(0.0);
		options.setTemperature(0.7);
		options.setTopK(50);
		options.setTopP(0.8);
		options.setStopSequences(List.of("stop1", "stop2"));
		options.setOutputSchema("{\"type\":\"object\"}");

		assertThat(options.getModel()).isEqualTo("test-model");
		assertThat(options.getFrequencyPenalty()).isEqualTo(0.0);
		assertThat(options.getMaxTokens()).isEqualTo(100);
		assertThat(options.getPresencePenalty()).isEqualTo(0.0);
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getTopK()).isEqualTo(50);
		assertThat(options.getTopP()).isEqualTo(0.8);
		assertThat(options.getStopSequences()).isEqualTo(List.of("stop1", "stop2"));
		assertThat(options.getOutputSchema()).isEqualTo("{\"type\":\"object\"}");
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

	@Test
	void testOutputSchemaOverwrite() {
		BedrockChatOptions options = BedrockChatOptions.builder().outputSchema("{\"type\":\"object\"}").build();

		options.setOutputSchema("{\"type\":\"array\"}");

		assertThat(options.getOutputSchema()).isEqualTo("{\"type\":\"array\"}");
	}

	@Test
	@RequiresAwsCredentials
	@SuppressWarnings("deprecation")
	void testDeprecatedBuilderMethods() {
		BedrockProxyChatModel chatModel = BedrockProxyChatModel.builder()
			.credentialsProvider(DefaultCredentialsProvider.builder().build())
			.region(Region.US_EAST_1)
			.timeout(Duration.ofMinutes(2))
			.connectionTimeout(Duration.ofSeconds(10))
			.asyncReadTimeout(Duration.ofSeconds(60))
			.connectionAcquisitionTimeout(Duration.ofSeconds(60))
			.socketTimeout(Duration.ofSeconds(60))
			.defaultOptions(BedrockChatOptions.builder().model("us.anthropic.claude-haiku-4-5-20251001-v1:0").build())
			.build();

		assertThat(chatModel).isNotNull();
	}

}
