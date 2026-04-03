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

package org.springframework.ai.openaisdk;

import java.util.Map;

import com.openai.models.chat.completions.ChatCompletionCreateParams;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests to verify that extraBody parameters are correctly passed to the OpenAI SDK
 * builder.
 *
 * @author Ilayaperumal Gopinathan
 *
 */
class OpenAiSdkExtraBodyTests {

	@Test
	void extraBodyIsMappedToAdditionalBodyProperties() {
		// Arrange
		Map<String, Object> extraBodyParams = Map.of("top_k", 50, "repetition_penalty", 1.1, "best_of", 3);

		OpenAiSdkChatOptions options = OpenAiSdkChatOptions.builder()
			.model("test-model")
			.extraBody(extraBodyParams)
			.build();

		Prompt prompt = new Prompt("Test prompt", options);
		OpenAiSdkChatModel chatModel = OpenAiSdkChatModel.builder()
			.openAiClient(org.mockito.Mockito.mock(com.openai.client.OpenAIClient.class))
			.openAiClientAsync(org.mockito.Mockito.mock(com.openai.client.OpenAIClientAsync.class))
			.build();

		// Act
		ChatCompletionCreateParams createParams = chatModel.createRequest(prompt, false);

		// Assert
		assertThat(createParams._additionalBodyProperties()).isNotNull();
		assertThat(createParams._additionalBodyProperties()).containsKeys("top_k", "repetition_penalty", "best_of");
		assertThat(createParams._additionalBodyProperties()).doesNotContainKey("extra_body");

		assertThat(createParams._additionalBodyProperties().get("top_k").asNumber().get()).isEqualTo(50);
		assertThat(createParams._additionalBodyProperties().get("repetition_penalty").asNumber().get()).isEqualTo(1.1);
		assertThat(createParams._additionalBodyProperties().get("best_of").asNumber().get()).isEqualTo(3);
	}

	@Test
	void extraBodyIsNotMappedWhenNullOrEmpty() {
		// Null extra body
		OpenAiSdkChatOptions optionsNull = OpenAiSdkChatOptions.builder().model("test-model").build();

		Prompt promptNull = new Prompt("Test prompt", optionsNull);
		OpenAiSdkChatModel chatModel = OpenAiSdkChatModel.builder()
			.openAiClient(org.mockito.Mockito.mock(com.openai.client.OpenAIClient.class))
			.openAiClientAsync(org.mockito.Mockito.mock(com.openai.client.OpenAIClientAsync.class))
			.build();

		ChatCompletionCreateParams createParamsNull = chatModel.createRequest(promptNull, false);
		assertThat(createParamsNull._additionalBodyProperties()).isEmpty();

		// Empty extra body
		OpenAiSdkChatOptions optionsEmpty = OpenAiSdkChatOptions.builder()
			.model("test-model")
			.extraBody(Map.of())
			.build();

		Prompt promptEmpty = new Prompt("Test prompt", optionsEmpty);

		ChatCompletionCreateParams createParamsEmpty = chatModel.createRequest(promptEmpty, false);
		assertThat(createParamsEmpty._additionalBodyProperties()).isEmpty();
	}

}
