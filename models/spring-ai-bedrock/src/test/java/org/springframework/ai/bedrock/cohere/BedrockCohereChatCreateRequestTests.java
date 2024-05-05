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

import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatModel;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatRequest;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatRequest.LogitBias;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatRequest.ReturnLikelihoods;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatRequest.Truncate;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class BedrockCohereChatCreateRequestTests {

	private CohereChatBedrockApi chatApi = new CohereChatBedrockApi(CohereChatModel.COHERE_COMMAND_V14.id(),
			EnvironmentVariableCredentialsProvider.create(), Region.US_EAST_1.id(), new ObjectMapper(),
			Duration.ofMinutes(2));

	@Test
	public void createRequestWithChatOptions() {

		var client = new BedrockCohereChatClient(chatApi,
				BedrockCohereChatOptions.builder()
					.withTemperature(66.6f)
					.withTopK(66)
					.withTopP(0.66f)
					.withMaxTokens(678)
					.withStopSequences(List.of("stop1", "stop2"))
					.withReturnLikelihoods(ReturnLikelihoods.ALL)
					.withNumGenerations(3)
					.withLogitBias(new LogitBias("t", 6.6f))
					.withTruncate(Truncate.END)
					.build());

		CohereChatRequest request = client.createRequest(new Prompt("Test message content"), true);

		assertThat(request.prompt()).isNotEmpty();
		assertThat(request.stream()).isTrue();

		assertThat(request.temperature()).isEqualTo(66.6f);
		assertThat(request.topK()).isEqualTo(66);
		assertThat(request.topP()).isEqualTo(0.66f);
		assertThat(request.maxTokens()).isEqualTo(678);
		assertThat(request.stopSequences()).containsExactly("stop1", "stop2");
		assertThat(request.returnLikelihoods()).isEqualTo(ReturnLikelihoods.ALL);
		assertThat(request.numGenerations()).isEqualTo(3);
		assertThat(request.logitBias()).isEqualTo(new LogitBias("t", 6.6f));
		assertThat(request.truncate()).isEqualTo(Truncate.END);

		request = client.createRequest(new Prompt("Test message content",
				BedrockCohereChatOptions.builder()
					.withTemperature(99.9f)
					.withTopK(99)
					.withTopP(0.99f)
					.withMaxTokens(888)
					.withStopSequences(List.of("stop3", "stop4"))
					.withReturnLikelihoods(ReturnLikelihoods.GENERATION)
					.withNumGenerations(13)
					.withLogitBias(new LogitBias("t", 9.9f))
					.withTruncate(Truncate.START)
					.build()),
				false

		);

		assertThat(request.prompt()).isNotEmpty();
		assertThat(request.stream()).isFalse();

		assertThat(request.temperature()).isEqualTo(99.9f);
		assertThat(request.topK()).isEqualTo(99);
		assertThat(request.topP()).isEqualTo(0.99f);
		assertThat(request.maxTokens()).isEqualTo(888);
		assertThat(request.stopSequences()).containsExactly("stop3", "stop4");
		assertThat(request.returnLikelihoods()).isEqualTo(ReturnLikelihoods.GENERATION);
		assertThat(request.numGenerations()).isEqualTo(13);
		assertThat(request.logitBias()).isEqualTo(new LogitBias("t", 9.9f));
		assertThat(request.truncate()).isEqualTo(Truncate.START);
	}

}
