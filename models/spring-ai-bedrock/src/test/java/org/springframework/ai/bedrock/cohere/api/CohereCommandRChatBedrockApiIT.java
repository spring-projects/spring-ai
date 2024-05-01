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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.cohere.api.CohereCommandRChatBedrockApi.ChatHistory;
import org.springframework.ai.bedrock.cohere.api.CohereCommandRChatBedrockApi.ChatHistory.Role;
import org.springframework.ai.bedrock.cohere.api.CohereCommandRChatBedrockApi.CohereCommandRChatModel;
import org.springframework.ai.bedrock.cohere.api.CohereCommandRChatBedrockApi.CohereCommandRChatRequest;
import org.springframework.ai.bedrock.cohere.api.CohereCommandRChatBedrockApi.CohereCommandRChatRequest.Document;
import org.springframework.ai.bedrock.cohere.api.CohereCommandRChatBedrockApi.CohereCommandRChatRequest.PromptTruncation;
import org.springframework.ai.bedrock.cohere.api.CohereCommandRChatBedrockApi.CohereCommandRChatResponse;
import org.springframework.ai.bedrock.cohere.api.CohereCommandRChatBedrockApi.CohereCommandRChatStreamingResponse;
import org.springframework.ai.bedrock.cohere.api.CohereCommandRChatBedrockApi.FinishReason;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Wei Jiang
 */
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".*")
public class CohereCommandRChatBedrockApiIT {

	private CohereCommandRChatBedrockApi cohereChatApi = new CohereCommandRChatBedrockApi(
			CohereCommandRChatModel.COHERE_COMMAND_R_PLUS_V1.id(), EnvironmentVariableCredentialsProvider.create(),
			Region.US_EAST_1.id(), new ObjectMapper(), Duration.ofMinutes(2));

	@Test
	public void requestBuilder() {

		CohereCommandRChatRequest request1 = new CohereCommandRChatRequest(
				"What is the capital of Bulgaria and what is the size? What it the national anthem?",
				List.of(new ChatHistory(Role.CHATBOT, "message")), List.of(new Document("title", "snippet")), false,
				"preamble", 100, 0.5f, 0.6f, 15, PromptTruncation.AUTO_PRESERVE_ORDER, 0.8f, 0.9f, 5050, false,
				List.of("stop_sequence"), false);

		var request2 = CohereCommandRChatRequest
			.builder("What is the capital of Bulgaria and what is the size? What it the national anthem?")
			.withChatHistory(List.of(new ChatHistory(Role.CHATBOT, "message")))
			.withDocuments(List.of(new Document("title", "snippet")))
			.withSearchQueriesOnly(false)
			.withPreamble("preamble")
			.withMaxTokens(100)
			.withTemperature(0.5f)
			.withTopP(0.6f)
			.withTopK(15)
			.withPromptTruncation(PromptTruncation.AUTO_PRESERVE_ORDER)
			.withFrequencyPenalty(0.8f)
			.withPresencePenalty(0.9f)
			.withSeed(5050)
			.withReturnPrompt(false)
			.withStopSequences(List.of("stop_sequence"))
			.withRawPrompting(false)
			.build();

		assertThat(request1).isEqualTo(request2);
	}

	@Test
	public void chatCompletion() {

		var request = CohereCommandRChatRequest
			.builder("What is the capital of Bulgaria and what is the size? What it the national anthem?")
			.withTemperature(0.5f)
			.withTopP(0.8f)
			.withTopK(15)
			.withMaxTokens(2000)
			.build();

		CohereCommandRChatResponse response = cohereChatApi.chatCompletion(request);

		assertThat(response).isNotNull();
		assertThat(response.finishReason()).isEqualTo(FinishReason.COMPLETE);
		assertThat(response.text()).isNotEmpty();
		assertThat(response.id()).isNotEmpty();
		assertThat(response.generationId()).isNotEmpty();
		assertThat(response.chatHistory()).isNotNull();
		assertThat(response.chatHistory().size()).isEqualTo(2);
	}

	@Test
	public void chatCompletionStream() {

		var request = CohereCommandRChatRequest
			.builder("What is the capital of Bulgaria and what is the size? What it the national anthem?")
			.withTemperature(0.5f)
			.withTopP(0.8f)
			.withTopK(15)
			.withMaxTokens(50)
			.withStopSequences(List.of("END"))
			.build();

		Flux<CohereCommandRChatStreamingResponse> responseStream = cohereChatApi.chatCompletionStream(request);
		List<CohereCommandRChatStreamingResponse> responses = responseStream.collectList().block();

		assertThat(responses).isNotNull();
		assertThat(responses).hasSizeGreaterThan(10);
		assertThat(responses.get(0).text()).isNotEmpty();
		CohereCommandRChatStreamingResponse lastResponse = responses.get(responses.size() - 1);
		assertThat(lastResponse.text()).isNull();
		assertThat(lastResponse.isFinished()).isTrue();
		assertThat(lastResponse.finishReason()).isEqualTo(FinishReason.MAX_TOKENS);
		assertThat(lastResponse.amazonBedrockInvocationMetrics()).isNotNull();
	}

}
