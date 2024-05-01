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
package org.springframework.ai.bedrock.cohere;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.cohere.api.CohereCommandRChatBedrockApi;
import org.springframework.ai.bedrock.cohere.api.CohereCommandRChatBedrockApi.CohereCommandRChatModel;
import org.springframework.ai.bedrock.cohere.api.CohereCommandRChatBedrockApi.CohereCommandRChatRequest;
import org.springframework.ai.bedrock.cohere.api.CohereCommandRChatBedrockApi.CohereCommandRChatRequest.PromptTruncation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Wei Jiang
 */
public class BedrockCohereCommandRChatCreateRequestTests {

	private CohereCommandRChatBedrockApi chatApi = new CohereCommandRChatBedrockApi(
			CohereCommandRChatModel.COHERE_COMMAND_R_PLUS_V1.id(), EnvironmentVariableCredentialsProvider.create(),
			Region.US_EAST_1.id(), new ObjectMapper(), Duration.ofMinutes(2));

	@Test
	public void createRequestWithChatOptions() {

		var client = new BedrockCohereCommandRChatModel(chatApi,
				BedrockCohereCommandRChatOptions.builder()
					.withSearchQueriesOnly(true)
					.withPreamble("preamble")
					.withMaxTokens(678)
					.withTemperature(66.6f)
					.withTopK(66)
					.withTopP(0.66f)
					.withPromptTruncation(PromptTruncation.OFF)
					.withFrequencyPenalty(0.1f)
					.withPresencePenalty(0.2f)
					.withSeed(1000)
					.withReturnPrompt(false)
					.withStopSequences(List.of("stop1", "stop2"))
					.withRawPrompting(false)
					.build());

		CohereCommandRChatRequest request = client.createRequest(new Prompt("Test message content"));

		assertThat(request.message()).isNotEmpty();
		assertThat(request.searchQueriesOnly()).isTrue();
		assertThat(request.preamble()).isEqualTo("preamble");
		assertThat(request.maxTokens()).isEqualTo(678);
		assertThat(request.temperature()).isEqualTo(66.6f);
		assertThat(request.topK()).isEqualTo(66);
		assertThat(request.topP()).isEqualTo(0.66f);
		assertThat(request.promptTruncation()).isEqualTo(PromptTruncation.OFF);
		assertThat(request.frequencyPenalty()).isEqualTo(0.1f);
		assertThat(request.presencePenalty()).isEqualTo(0.2f);
		assertThat(request.seed()).isEqualTo(1000);
		assertThat(request.returnPrompt()).isEqualTo(false);
		assertThat(request.stopSequences()).containsExactly("stop1", "stop2");
		assertThat(request.rawPrompting()).isEqualTo(false);

		request = client.createRequest(new Prompt("Test message content",
				BedrockCohereCommandRChatOptions.builder()
					.withSearchQueriesOnly(false)
					.withPreamble("preamble")
					.withMaxTokens(999)
					.withTemperature(99.9f)
					.withTopK(99)
					.withTopP(0.99f)
					.withPromptTruncation(PromptTruncation.OFF)
					.withFrequencyPenalty(0.9f)
					.withPresencePenalty(0.9f)
					.withSeed(9999)
					.withReturnPrompt(true)
					.withStopSequences(List.of("stop1", "stop2"))
					.withRawPrompting(true)
					.build()));

		assertThat(request.message()).isNotEmpty();
		assertThat(request.searchQueriesOnly()).isFalse();
		assertThat(request.preamble()).isEqualTo("preamble");
		assertThat(request.maxTokens()).isEqualTo(999);
		assertThat(request.temperature()).isEqualTo(99.9f);
		assertThat(request.topK()).isEqualTo(99);
		assertThat(request.topP()).isEqualTo(0.99f);
		assertThat(request.promptTruncation()).isEqualTo(PromptTruncation.OFF);
		assertThat(request.frequencyPenalty()).isEqualTo(0.9f);
		assertThat(request.presencePenalty()).isEqualTo(0.9f);
		assertThat(request.seed()).isEqualTo(9999);
		assertThat(request.returnPrompt()).isEqualTo(true);
		assertThat(request.stopSequences()).containsExactly("stop1", "stop2");
		assertThat(request.rawPrompting()).isEqualTo(true);
	}

}
