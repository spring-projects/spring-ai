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
package org.springframework.ai.bedrock.mistral;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.mistral.api.MistralChatBedrockApi;
import org.springframework.ai.bedrock.mistral.api.MistralChatBedrockApi.MistralChatModel;
import org.springframework.ai.bedrock.mistral.api.MistralChatBedrockApi.MistralChatRequest;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Wei Jiang
 * @since 1.0.0
 */
public class BedrockMistralChatCreateRequestTests {

	private MistralChatBedrockApi chatApi = new MistralChatBedrockApi(MistralChatModel.MISTRAL_8X7B_INSTRUCT.id(),
			EnvironmentVariableCredentialsProvider.create(), Region.US_EAST_1.id(), new ObjectMapper(),
			Duration.ofMinutes(2));

	@Test
	public void createRequestWithChatOptions() {

		var client = new BedrockMistralChatModel(chatApi,
				BedrockMistralChatOptions.builder()
					.withTemperature(66.6f)
					.withTopK(66)
					.withTopP(0.66f)
					.withMaxTokens(678)
					.withStopSequences(List.of("stop1", "stop2"))
					.build());

		MistralChatRequest request = client.createRequest(new Prompt("Test message content"));

		assertThat(request.prompt()).isNotEmpty();

		assertThat(request.temperature()).isEqualTo(66.6f);
		assertThat(request.topK()).isEqualTo(66);
		assertThat(request.topP()).isEqualTo(0.66f);
		assertThat(request.maxTokens()).isEqualTo(678);
		assertThat(request.stopSequences()).containsExactly("stop1", "stop2");
	}

}
