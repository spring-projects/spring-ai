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
package org.springframework.ai.bedrock.cohere.api;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatModel;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatRequest;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatRequest.Truncate;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatResponse;
import org.springframework.ai.bedrock.cohere.api.CohereChatBedrockApi.CohereChatResponse.Generation.FinishReason;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;;

/**
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".*")
public class CohereChatBedrockApiIT {

	private CohereChatBedrockApi cohereChatApi = new CohereChatBedrockApi(CohereChatModel.COHERE_COMMAND_V14.id(),
			Region.US_EAST_1.id(), Duration.ofMinutes(2));

	@Test
	public void requestBuilder() {

		CohereChatRequest request1 = new CohereChatRequest(
				"What is the capital of Bulgaria and what is the size? What it the national anthem?", 0.5f, 0.9f, 15,
				40, List.of("END"), CohereChatRequest.ReturnLikelihoods.ALL, false, 1, null, Truncate.NONE);

		var request2 = CohereChatRequest
			.builder("What is the capital of Bulgaria and what is the size? What it the national anthem?")
			.withTemperature(0.5f)
			.withTopP(0.9f)
			.withTopK(15)
			.withMaxTokens(40)
			.withStopSequences(List.of("END"))
			.withReturnLikelihoods(CohereChatRequest.ReturnLikelihoods.ALL)
			.withStream(false)
			.withNumGenerations(1)
			.withLogitBias(null)
			.withTruncate(Truncate.NONE)
			.build();

		assertThat(request1).isEqualTo(request2);
	}

	@Test
	public void chatCompletion() {

		var request = CohereChatRequest
			.builder("What is the capital of Bulgaria and what is the size? What it the national anthem?")
			.withStream(false)
			.withTemperature(0.5f)
			.withTopP(0.8f)
			.withTopK(15)
			.withMaxTokens(100)
			.withStopSequences(List.of("END"))
			.withReturnLikelihoods(CohereChatRequest.ReturnLikelihoods.ALL)
			.withNumGenerations(3)
			.withLogitBias(null)
			.withTruncate(Truncate.NONE)
			.build();

		CohereChatResponse response = cohereChatApi.chatCompletion(request);

		assertThat(response).isNotNull();
		assertThat(response.prompt()).isEqualTo(request.prompt());
		assertThat(response.generations()).hasSize(request.numGenerations());
		assertThat(response.generations().get(0).text()).isNotEmpty();
	}

	@Test
	public void chatCompletionStream() {

		var request = CohereChatRequest
			.builder("What is the capital of Bulgaria and what is the size? What it the national anthem?")
			.withStream(true)
			.withTemperature(0.5f)
			.withTopP(0.8f)
			.withTopK(15)
			.withMaxTokens(100)
			.withStopSequences(List.of("END"))
			.withReturnLikelihoods(CohereChatRequest.ReturnLikelihoods.ALL)
			.withNumGenerations(3)
			.withLogitBias(null)
			.withTruncate(Truncate.NONE)
			.build();

		Flux<CohereChatResponse.Generation> responseStream = cohereChatApi.chatCompletionStream(request);
		List<CohereChatResponse.Generation> responses = responseStream.collectList().block();

		assertThat(responses).isNotNull();
		assertThat(responses).hasSizeGreaterThan(10);
		assertThat(responses.get(0).text()).isNotEmpty();

		CohereChatResponse.Generation lastResponse = responses.get(responses.size() - 1);
		assertThat(lastResponse.text()).isNull();
		assertThat(lastResponse.isFinished()).isTrue();
		assertThat(lastResponse.finishReason()).isEqualTo(FinishReason.MAX_TOKENS);
		assertThat(lastResponse.amazonBedrockInvocationMetrics()).isNotNull();
	}

	@Test
	public void testStreamConfigurations() {
		var streamRequest = CohereChatRequest
			.builder("What is the capital of Bulgaria and what is the size? What it the national anthem?")
			.withStream(true)
			.build();

		assertThatThrownBy(() -> cohereChatApi.chatCompletion(streamRequest))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("The request must be configured to return the complete response!");

		var notStreamRequest = CohereChatRequest
			.builder("What is the capital of Bulgaria and what is the size? What it the national anthem?")
			.withStream(false)
			.build();

		assertThatThrownBy(() -> cohereChatApi.chatCompletionStream(notStreamRequest))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("The request must be configured to stream the response!");

	}

}
