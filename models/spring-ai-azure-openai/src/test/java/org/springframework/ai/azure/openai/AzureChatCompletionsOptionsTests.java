/*
 * Copyright 2023-2024 the original author or authors.
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
import java.util.stream.Stream;

import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.AzureChatEnhancementConfiguration;
import com.azure.ai.openai.models.ChatCompletionsJsonResponseFormat;
import com.azure.ai.openai.models.ChatCompletionsTextResponseFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import org.springframework.ai.azure.openai.AzureOpenAiResponseFormat.Type;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Soby Chacko
 */
public class AzureChatCompletionsOptionsTests {

	private static Stream<Arguments> providePresencePenaltyAndFrequencyPenaltyTest() {
		return Stream.of(Arguments.of(0.0, 0.0), Arguments.of(0.0, 1.0), Arguments.of(1.0, 0.0), Arguments.of(1.0, 1.0),
				Arguments.of(1.0, null), Arguments.of(null, 1.0), Arguments.of(null, null));
	}

	@Test
	public void createRequestWithChatOptions() {

		OpenAIClientBuilder mockClient = Mockito.mock(OpenAIClientBuilder.class);

		AzureChatEnhancementConfiguration mockAzureChatEnhancementConfiguration = Mockito
			.mock(AzureChatEnhancementConfiguration.class);

		var defaultOptions = AzureOpenAiChatOptions.builder()
			.deploymentName("DEFAULT_MODEL")
			.temperature(66.6)
			.frequencyPenalty(696.9)
			.presencePenalty(969.6)
			.logitBias(Map.of("foo", 1))
			.maxTokens(969)
			.N(69)
			.stop(List.of("foo", "bar"))
			.topP(0.69)
			.user("user")
			.seed(123L)
			.logprobs(true)
			.topLogprobs(5)
			.enhancements(mockAzureChatEnhancementConfiguration)
			.responseFormat(AzureOpenAiResponseFormat.builder().type(Type.TEXT).build())
			.build();

		var client = AzureOpenAiChatModel.builder()
			.openAIClientBuilder(mockClient)
			.defaultOptions(defaultOptions)
			.build();

		var requestOptions = client.toAzureChatCompletionsOptions(new Prompt("Test message content"));

		assertThat(requestOptions.getMessages()).hasSize(1);

		assertThat(requestOptions.getModel()).isEqualTo("DEFAULT_MODEL");
		assertThat(requestOptions.getTemperature()).isEqualTo(66.6);
		assertThat(requestOptions.getFrequencyPenalty()).isEqualTo(696.9);
		assertThat(requestOptions.getPresencePenalty()).isEqualTo(969.6);
		assertThat(requestOptions.getLogitBias()).isEqualTo(Map.of("foo", 1));
		assertThat(requestOptions.getMaxTokens()).isEqualTo(969);
		assertThat(requestOptions.getN()).isEqualTo(69);
		assertThat(requestOptions.getStop()).isEqualTo(List.of("foo", "bar"));
		assertThat(requestOptions.getTopP()).isEqualTo(0.69);
		assertThat(requestOptions.getUser()).isEqualTo("user");
		assertThat(requestOptions.getSeed()).isEqualTo(123L);
		assertThat(requestOptions.isLogprobs()).isTrue();
		assertThat(requestOptions.getTopLogprobs()).isEqualTo(5);
		assertThat(requestOptions.getEnhancements()).isEqualTo(mockAzureChatEnhancementConfiguration);
		assertThat(requestOptions.getResponseFormat()).isInstanceOf(ChatCompletionsTextResponseFormat.class);

		AzureChatEnhancementConfiguration anotherMockAzureChatEnhancementConfiguration = Mockito
			.mock(AzureChatEnhancementConfiguration.class);

		var runtimeOptions = AzureOpenAiChatOptions.builder()
			.deploymentName("PROMPT_MODEL")
			.temperature(99.9)
			.frequencyPenalty(100.0)
			.presencePenalty(100.0)
			.logitBias(Map.of("foo", 2))
			.maxTokens(100)
			.N(100)
			.stop(List.of("foo", "bar"))
			.topP(0.111)
			.user("user2")
			.seed(1234L)
			.logprobs(true)
			.topLogprobs(4)
			.enhancements(anotherMockAzureChatEnhancementConfiguration)
			.responseFormat(AzureOpenAiResponseFormat.builder().type(Type.JSON_OBJECT).build())
			.build();

		requestOptions = client.toAzureChatCompletionsOptions(new Prompt("Test message content", runtimeOptions));

		assertThat(requestOptions.getMessages()).hasSize(1);

		assertThat(requestOptions.getModel()).isEqualTo("PROMPT_MODEL");
		assertThat(requestOptions.getTemperature()).isEqualTo(99.9);
		assertThat(requestOptions.getFrequencyPenalty()).isEqualTo(100.0);
		assertThat(requestOptions.getPresencePenalty()).isEqualTo(100.0);
		assertThat(requestOptions.getLogitBias()).isEqualTo(Map.of("foo", 2));
		assertThat(requestOptions.getMaxTokens()).isEqualTo(100);
		assertThat(requestOptions.getN()).isEqualTo(100);
		assertThat(requestOptions.getStop()).isEqualTo(List.of("foo", "bar"));
		assertThat(requestOptions.getTopP()).isEqualTo(0.111);
		assertThat(requestOptions.getUser()).isEqualTo("user2");
		assertThat(requestOptions.getSeed()).isEqualTo(1234L);
		assertThat(requestOptions.isLogprobs()).isTrue();
		assertThat(requestOptions.getTopLogprobs()).isEqualTo(4);
		assertThat(requestOptions.getEnhancements()).isEqualTo(anotherMockAzureChatEnhancementConfiguration);
		assertThat(requestOptions.getResponseFormat()).isInstanceOf(ChatCompletionsJsonResponseFormat.class);
	}

	@ParameterizedTest
	@MethodSource("providePresencePenaltyAndFrequencyPenaltyTest")
	public void createChatOptionsWithPresencePenaltyAndFrequencyPenalty(Double presencePenalty,
			Double frequencyPenalty) {
		var options = AzureOpenAiChatOptions.builder()
			.maxTokens(800)
			.temperature(0.7)
			.topP(0.95)
			.presencePenalty(presencePenalty)
			.frequencyPenalty(frequencyPenalty)
			.build();

		if (presencePenalty == null) {
			assertThat(options.getPresencePenalty()).isEqualTo(null);
		}
		else {
			assertThat(options.getPresencePenalty()).isEqualTo(presencePenalty);
		}

		if (frequencyPenalty == null) {
			assertThat(options.getFrequencyPenalty()).isEqualTo(null);
		}
		else {
			assertThat(options.getFrequencyPenalty()).isEqualTo(frequencyPenalty);
		}
	}

	@Test
	public void createRequestWithMinimalOptions() {
		OpenAIClientBuilder mockClient = Mockito.mock(OpenAIClientBuilder.class);

		var minimalOptions = AzureOpenAiChatOptions.builder().deploymentName("MINIMAL_MODEL").build();

		var client = AzureOpenAiChatModel.builder()
			.openAIClientBuilder(mockClient)
			.defaultOptions(minimalOptions)
			.build();

		var requestOptions = client.toAzureChatCompletionsOptions(new Prompt("Test message"));

		assertThat(requestOptions.getModel()).isEqualTo("MINIMAL_MODEL");
		assertThat(requestOptions.getTemperature()).isNull();
		assertThat(requestOptions.getMaxTokens()).isNull();
		assertThat(requestOptions.getTopP()).isNull();
	}

	@Test
	public void createRequestWithEmptyStopList() {
		OpenAIClientBuilder mockClient = Mockito.mock(OpenAIClientBuilder.class);

		var options = AzureOpenAiChatOptions.builder().deploymentName("TEST_MODEL").stop(List.of()).build();

		var client = AzureOpenAiChatModel.builder().openAIClientBuilder(mockClient).defaultOptions(options).build();

		var requestOptions = client.toAzureChatCompletionsOptions(new Prompt("Test message"));

		assertThat(requestOptions.getStop()).isEmpty();
	}

	@Test
	public void createRequestWithEmptyLogitBias() {
		OpenAIClientBuilder mockClient = Mockito.mock(OpenAIClientBuilder.class);

		var options = AzureOpenAiChatOptions.builder().deploymentName("TEST_MODEL").logitBias(Map.of()).build();

		var client = AzureOpenAiChatModel.builder().openAIClientBuilder(mockClient).defaultOptions(options).build();

		var requestOptions = client.toAzureChatCompletionsOptions(new Prompt("Test message"));

		assertThat(requestOptions.getLogitBias()).isEmpty();
	}

	@Test
	public void createRequestWithLogprobsDisabled() {
		OpenAIClientBuilder mockClient = Mockito.mock(OpenAIClientBuilder.class);

		var options = AzureOpenAiChatOptions.builder()
			.deploymentName("TEST_MODEL")
			.logprobs(false)
			.topLogprobs(0)
			.build();

		var client = AzureOpenAiChatModel.builder().openAIClientBuilder(mockClient).defaultOptions(options).build();

		var requestOptions = client.toAzureChatCompletionsOptions(new Prompt("Test message"));

		assertThat(requestOptions.isLogprobs()).isFalse();
		assertThat(requestOptions.getTopLogprobs()).isEqualTo(0);
	}

	@Test
	public void createRequestWithSingleStopSequence() {
		OpenAIClientBuilder mockClient = Mockito.mock(OpenAIClientBuilder.class);

		var options = AzureOpenAiChatOptions.builder().deploymentName("SINGLE_STOP_MODEL").stop(List.of("END")).build();

		var client = AzureOpenAiChatModel.builder().openAIClientBuilder(mockClient).defaultOptions(options).build();

		var requestOptions = client.toAzureChatCompletionsOptions(new Prompt("Test message"));

		assertThat(requestOptions.getStop()).hasSize(1);
		assertThat(requestOptions.getStop()).containsExactly("END");
	}

	@Test
	public void builderPatternTest() {
		var options = AzureOpenAiChatOptions.builder()
			.deploymentName("BUILDER_TEST_MODEL")
			.temperature(0.7)
			.maxTokens(1500)
			.build();

		assertThat(options.getDeploymentName()).isEqualTo("BUILDER_TEST_MODEL");
		assertThat(options.getTemperature()).isEqualTo(0.7);
		assertThat(options.getMaxTokens()).isEqualTo(1500);
	}

	@ParameterizedTest
	@MethodSource("provideResponseFormatTypes")
	public void createRequestWithDifferentResponseFormats(Type responseFormatType, Class<?> expectedFormatClass) {
		OpenAIClientBuilder mockClient = Mockito.mock(OpenAIClientBuilder.class);

		var options = AzureOpenAiChatOptions.builder()
			.deploymentName("FORMAT_TEST_MODEL")
			.responseFormat(AzureOpenAiResponseFormat.builder().type(responseFormatType).build())
			.build();

		var client = AzureOpenAiChatModel.builder().openAIClientBuilder(mockClient).defaultOptions(options).build();

		var requestOptions = client.toAzureChatCompletionsOptions(new Prompt("Test message"));

		assertThat(requestOptions.getResponseFormat()).isInstanceOf(expectedFormatClass);
	}

	private static Stream<Arguments> provideResponseFormatTypes() {
		return Stream.of(Arguments.of(Type.TEXT, ChatCompletionsTextResponseFormat.class),
				Arguments.of(Type.JSON_OBJECT, ChatCompletionsJsonResponseFormat.class));
	}

}
