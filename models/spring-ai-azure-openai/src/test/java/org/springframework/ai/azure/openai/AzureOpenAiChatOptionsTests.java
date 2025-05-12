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

package org.springframework.ai.azure.openai;

import java.util.List;
import java.util.Map;

import com.azure.ai.openai.models.AzureChatEnhancementConfiguration;
import com.azure.ai.openai.models.AzureChatGroundingEnhancementConfiguration;
import com.azure.ai.openai.models.AzureChatOCREnhancementConfiguration;
import com.azure.ai.openai.models.ChatCompletionStreamOptions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AzureOpenAiChatOptions}.
 *
 * @author Alexandros Pappas
 */
class AzureOpenAiChatOptionsTests {

	@Test
	void testBuilderWithAllFields() {
		AzureOpenAiResponseFormat responseFormat = AzureOpenAiResponseFormat.builder()
			.type(AzureOpenAiResponseFormat.Type.TEXT)
			.build();
		ChatCompletionStreamOptions streamOptions = new ChatCompletionStreamOptions();
		streamOptions.setIncludeUsage(true);

		AzureChatEnhancementConfiguration enhancements = new AzureChatEnhancementConfiguration();
		enhancements.setOcr(new AzureChatOCREnhancementConfiguration(true));
		enhancements.setGrounding(new AzureChatGroundingEnhancementConfiguration(true));

		AzureOpenAiChatOptions options = AzureOpenAiChatOptions.builder()
			.deploymentName("test-deployment")
			.frequencyPenalty(0.5)
			.logitBias(Map.of("token1", 1, "token2", -1))
			.maxTokens(200)
			.N(2)
			.presencePenalty(0.8)
			.stop(List.of("stop1", "stop2"))
			.temperature(0.7)
			.topP(0.9)
			.user("test-user")
			.responseFormat(responseFormat)
			.streamUsage(true)
			.reasoningEffort("low")
			.seed(12345L)
			.logprobs(true)
			.topLogprobs(5)
			.enhancements(enhancements)
			.streamOptions(streamOptions)
			.build();

		assertThat(options)
			.extracting("deploymentName", "frequencyPenalty", "logitBias", "maxTokens", "n", "presencePenalty", "stop",
					"temperature", "topP", "user", "responseFormat", "streamUsage", "reasoningEffort", "seed",
					"logprobs", "topLogProbs", "enhancements", "streamOptions")
			.containsExactly("test-deployment", 0.5, Map.of("token1", 1, "token2", -1), 200, 2, 0.8,
					List.of("stop1", "stop2"), 0.7, 0.9, "test-user", responseFormat, true, "low", 12345L, true, 5,
					enhancements, streamOptions);
	}

	@Test
	void testCopy() {
		AzureOpenAiResponseFormat responseFormat = AzureOpenAiResponseFormat.builder()
			.type(AzureOpenAiResponseFormat.Type.TEXT)
			.build();
		ChatCompletionStreamOptions streamOptions = new ChatCompletionStreamOptions();
		streamOptions.setIncludeUsage(true);

		AzureChatEnhancementConfiguration enhancements = new AzureChatEnhancementConfiguration();
		enhancements.setOcr(new AzureChatOCREnhancementConfiguration(true));
		enhancements.setGrounding(new AzureChatGroundingEnhancementConfiguration(true));

		AzureOpenAiChatOptions originalOptions = AzureOpenAiChatOptions.builder()
			.deploymentName("test-deployment")
			.frequencyPenalty(0.5)
			.logitBias(Map.of("token1", 1, "token2", -1))
			.maxTokens(200)
			.N(2)
			.presencePenalty(0.8)
			.stop(List.of("stop1", "stop2"))
			.temperature(0.7)
			.topP(0.9)
			.user("test-user")
			.responseFormat(responseFormat)
			.streamUsage(true)
			.reasoningEffort("low")
			.seed(12345L)
			.logprobs(true)
			.topLogprobs(5)
			.enhancements(enhancements)
			.streamOptions(streamOptions)
			.build();

		AzureOpenAiChatOptions copiedOptions = originalOptions.copy();

		assertThat(copiedOptions).isNotSameAs(originalOptions).isEqualTo(originalOptions);
		// Ensure deep copy
		assertThat(copiedOptions.getStop()).isNotSameAs(originalOptions.getStop());
		assertThat(copiedOptions.getToolContext()).isNotSameAs(originalOptions.getToolContext());
	}

	@Test
	void testSetters() {
		AzureOpenAiResponseFormat responseFormat = AzureOpenAiResponseFormat.builder()
			.type(AzureOpenAiResponseFormat.Type.TEXT)
			.build();
		ChatCompletionStreamOptions streamOptions = new ChatCompletionStreamOptions();
		streamOptions.setIncludeUsage(true);
		AzureChatEnhancementConfiguration enhancements = new AzureChatEnhancementConfiguration();

		AzureOpenAiChatOptions options = new AzureOpenAiChatOptions();
		options.setDeploymentName("test-deployment");
		options.setFrequencyPenalty(0.5);
		options.setLogitBias(Map.of("token1", 1, "token2", -1));
		options.setMaxTokens(200);
		options.setN(2);
		options.setPresencePenalty(0.8);
		options.setStop(List.of("stop1", "stop2"));
		options.setTemperature(0.7);
		options.setTopP(0.9);
		options.setUser("test-user");
		options.setResponseFormat(responseFormat);
		options.setStreamUsage(true);
		options.setReasoningEffort("low");
		options.setSeed(12345L);
		options.setLogprobs(true);
		options.setTopLogProbs(5);
		options.setEnhancements(enhancements);
		options.setStreamOptions(streamOptions);

		assertThat(options.getDeploymentName()).isEqualTo("test-deployment");
		options.setModel("test-model");
		assertThat(options.getDeploymentName()).isEqualTo("test-model");

		assertThat(options.getFrequencyPenalty()).isEqualTo(0.5);
		assertThat(options.getLogitBias()).isEqualTo(Map.of("token1", 1, "token2", -1));
		assertThat(options.getMaxTokens()).isEqualTo(200);
		assertThat(options.getN()).isEqualTo(2);
		assertThat(options.getPresencePenalty()).isEqualTo(0.8);
		assertThat(options.getStop()).isEqualTo(List.of("stop1", "stop2"));
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getTopP()).isEqualTo(0.9);
		assertThat(options.getUser()).isEqualTo("test-user");
		assertThat(options.getResponseFormat()).isEqualTo(responseFormat);
		assertThat(options.getStreamUsage()).isTrue();
		assertThat(options.getReasoningEffort()).isEqualTo("low");
		assertThat(options.getSeed()).isEqualTo(12345L);
		assertThat(options.isLogprobs()).isTrue();
		assertThat(options.getTopLogProbs()).isEqualTo(5);
		assertThat(options.getEnhancements()).isEqualTo(enhancements);
		assertThat(options.getStreamOptions()).isEqualTo(streamOptions);
		assertThat(options.getModel()).isEqualTo("test-model");
	}

	@Test
	void testDefaultValues() {
		AzureOpenAiChatOptions options = new AzureOpenAiChatOptions();

		assertThat(options.getDeploymentName()).isNull();
		assertThat(options.getFrequencyPenalty()).isNull();
		assertThat(options.getLogitBias()).isNull();
		assertThat(options.getMaxTokens()).isNull();
		assertThat(options.getN()).isNull();
		assertThat(options.getPresencePenalty()).isNull();
		assertThat(options.getStop()).isNull();
		assertThat(options.getTemperature()).isNull();
		assertThat(options.getTopP()).isNull();
		assertThat(options.getUser()).isNull();
		assertThat(options.getResponseFormat()).isNull();
		assertThat(options.getStreamUsage()).isNull();
		assertThat(options.getReasoningEffort()).isNull();
		assertThat(options.getSeed()).isNull();
		assertThat(options.isLogprobs()).isNull();
		assertThat(options.getTopLogProbs()).isNull();
		assertThat(options.getEnhancements()).isNull();
		assertThat(options.getStreamOptions()).isNull();
		assertThat(options.getModel()).isNull();
	}

}
