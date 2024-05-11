/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.azure.openai;

import com.azure.ai.openai.OpenAIClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;

import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class AzureChatCompletionsOptionsTests {

	@Test
	public void createRequestWithChatOptions() {

		OpenAIClient mockClient = Mockito.mock(OpenAIClient.class);

		var defaultOptions = AzureOpenAiChatOptions.builder()
			.withDeploymentName("DEFAULT_MODEL")
			.withTemperature(66.6f)
			.withFrequencyPenalty(696.9f)
			.withPresencePenalty(969.6f)
			.withLogitBias(Map.of("foo", 1))
			.withMaxTokens(969)
			.withN(69)
			.withStop(List.of("foo", "bar"))
			.withTopP(0.69f)
			.withUser("user")
			.build();

		var client = new AzureOpenAiChatClient(mockClient, defaultOptions);

		var requestOptions = client.toAzureChatCompletionsOptions(new Prompt("Test message content"));

		assertThat(requestOptions.getMessages()).hasSize(1);

		assertThat(requestOptions.getModel()).isEqualTo("DEFAULT_MODEL");
		assertThat(requestOptions.getTemperature()).isEqualTo(66.6f);
		assertThat(requestOptions.getFrequencyPenalty()).isEqualTo(696.9f);
		assertThat(requestOptions.getPresencePenalty()).isEqualTo(969.6f);
		assertThat(requestOptions.getLogitBias()).isEqualTo(Map.of("foo", 1));
		assertThat(requestOptions.getMaxTokens()).isEqualTo(969);
		assertThat(requestOptions.getN()).isEqualTo(69);
		assertThat(requestOptions.getStop()).isEqualTo(List.of("foo", "bar"));
		assertThat(requestOptions.getTopP()).isEqualTo(0.69f);
		assertThat(requestOptions.getUser()).isEqualTo("user");

		var runtimeOptions = AzureOpenAiChatOptions.builder()
			.withDeploymentName("PROMPT_MODEL")
			.withTemperature(99.9f)
			.withFrequencyPenalty(100f)
			.withPresencePenalty(100f)
			.withLogitBias(Map.of("foo", 2))
			.withMaxTokens(100)
			.withN(100)
			.withStop(List.of("foo", "bar"))
			.withTopP(0.111f)
			.withUser("user2")
			.build();

		requestOptions = client.toAzureChatCompletionsOptions(new Prompt("Test message content", runtimeOptions));

		assertThat(requestOptions.getMessages()).hasSize(1);

		assertThat(requestOptions.getModel()).isEqualTo("PROMPT_MODEL");
		assertThat(requestOptions.getTemperature()).isEqualTo(99.9f);
		assertThat(requestOptions.getFrequencyPenalty()).isEqualTo(100f);
		assertThat(requestOptions.getPresencePenalty()).isEqualTo(100f);
		assertThat(requestOptions.getLogitBias()).isEqualTo(Map.of("foo", 2));
		assertThat(requestOptions.getMaxTokens()).isEqualTo(100);
		assertThat(requestOptions.getN()).isEqualTo(100);
		assertThat(requestOptions.getStop()).isEqualTo(List.of("foo", "bar"));
		assertThat(requestOptions.getTopP()).isEqualTo(0.111f);
		assertThat(requestOptions.getUser()).isEqualTo("user2");
	}

	private static Stream<Arguments> providePresencePenaltyAndFrequencyPenaltyTest() {
		return Stream.of(Arguments.of(0.0f, 0.0f), Arguments.of(0.0f, 1.0f), Arguments.of(1.0f, 0.0f),
				Arguments.of(1.0f, 1.0f), Arguments.of(1.0f, null), Arguments.of(null, 1.0f), Arguments.of(null, null));
	}

	@ParameterizedTest
	@MethodSource("providePresencePenaltyAndFrequencyPenaltyTest")
	public void createChatOptionsWithPresencePenaltyAndFrequencyPenalty(Float presencePenalty, Float frequencyPenalty) {
		var options = AzureOpenAiChatOptions.builder()
			.withMaxTokens(800)
			.withTemperature(0.7F)
			.withTopP(0.95F)
			.withPresencePenalty(presencePenalty)
			.withFrequencyPenalty(frequencyPenalty)
			.build();

		if (presencePenalty == null) {
			assertThat(options.getPresencePenalty()).isEqualTo(null);
		}
		else {
			assertThat(options.getPresencePenalty().floatValue()).isEqualTo(presencePenalty);
		}

		if (frequencyPenalty == null) {
			assertThat(options.getFrequencyPenalty()).isEqualTo(null);
		}
		else {
			assertThat(options.getFrequencyPenalty().floatValue()).isEqualTo(frequencyPenalty);
		}
	}

}
