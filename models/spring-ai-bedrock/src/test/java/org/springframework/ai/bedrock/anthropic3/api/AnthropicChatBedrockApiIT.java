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
package org.springframework.ai.bedrock.anthropic3.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.bedrock.anthropic3.api.AnthropicChatBedrockApi.AnthropicChatModel;
import org.springframework.ai.bedrock.anthropic3.api.AnthropicChatBedrockApi.AnthropicChatRequest;
import org.springframework.ai.bedrock.anthropic3.api.AnthropicChatBedrockApi.AnthropicChatResponse;
import org.springframework.ai.bedrock.anthropic3.api.AnthropicChatBedrockApi.AnthropicChatStreamingResponse.StreamingType;
import org.springframework.ai.bedrock.anthropic3.api.AnthropicChatBedrockApi.AnthropicMessage;
import org.springframework.ai.bedrock.anthropic3.api.AnthropicChatBedrockApi.ChatCompletionMessage;
import org.springframework.ai.bedrock.anthropic3.api.AnthropicChatBedrockApi.ChatCompletionMessage.Role;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.bedrock.anthropic3.api.AnthropicChatBedrockApi.DEFAULT_ANTHROPIC_VERSION;

;

/**
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".*")
public class AnthropicChatBedrockApiIT {

	private final Logger logger = LoggerFactory.getLogger(AnthropicChatBedrockApiIT.class);

	private AnthropicChatBedrockApi anthropicChatApi = new AnthropicChatBedrockApi(
			AnthropicChatModel.CLAUDE_INSTANT_V1.id(), EnvironmentVariableCredentialsProvider.create(),
			Region.US_WEST_2.id(), new ObjectMapper());

	@Test
	public void chatCompletion() {

		AnthropicMessage anthropicMessage = new AnthropicMessage(AnthropicMessage.Type.TEXT, "Name 3 famous pirates");
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage(List.of(anthropicMessage), Role.USER);
		AnthropicChatRequest request = AnthropicChatRequest.builder(List.of(chatCompletionMessage))
			.withTemperature(0.8f)
			.withMaxTokens(300)
			.withTopK(10)
			.withAnthropicVersion(DEFAULT_ANTHROPIC_VERSION)
			.build();

		AnthropicChatResponse response = anthropicChatApi.chatCompletion(request);

		System.out.println(response.content());
		assertThat(response).isNotNull();
		assertThat(response.content().get(0).text()).isNotEmpty();
		assertThat(response.content().get(0).text()).contains("Blackbeard");
		assertThat(response.stopReason()).isEqualTo("end_turn");
		assertThat(response.stopSequence()).isNull();
		assertThat(response.usage().inputTokens()).isGreaterThan(10);
		assertThat(response.usage().outputTokens()).isGreaterThan(100);

		logger.info("" + response);
	}

	@Test
	public void chatMultiCompletion() {

		AnthropicMessage anthropicInitialMessage = new AnthropicMessage(AnthropicMessage.Type.TEXT,
				"Name 3 famous pirates");
		ChatCompletionMessage chatCompletionInitialMessage = new ChatCompletionMessage(List.of(anthropicInitialMessage),
				Role.USER);

		AnthropicMessage anthropicAssistantMessage = new AnthropicMessage(AnthropicMessage.Type.TEXT,
				"Here are 3 famous pirates: Blackbeard, Calico Jack, Henry Morgan");
		ChatCompletionMessage chatCompletionAssistantMessage = new ChatCompletionMessage(
				List.of(anthropicAssistantMessage), Role.ASSISTANT);

		AnthropicMessage anthropicFollowupMessage = new AnthropicMessage(AnthropicMessage.Type.TEXT,
				"Why are they famous?");
		ChatCompletionMessage chatCompletionFollowupMessage = new ChatCompletionMessage(
				List.of(anthropicFollowupMessage), Role.USER);

		AnthropicChatRequest request = AnthropicChatRequest
			.builder(List.of(chatCompletionInitialMessage, chatCompletionAssistantMessage,
					chatCompletionFollowupMessage))
			.withTemperature(0.8f)
			.withMaxTokens(400)
			.withTopK(10)
			.withAnthropicVersion(DEFAULT_ANTHROPIC_VERSION)
			.build();

		AnthropicChatResponse response = anthropicChatApi.chatCompletion(request);

		System.out.println(response.content());
		assertThat(response).isNotNull();
		assertThat(response.content().get(0).text()).isNotEmpty();
		assertThat(response.content().get(0).text()).contains("Blackbeard");
		assertThat(response.stopReason()).isEqualTo("end_turn");
		assertThat(response.stopSequence()).isNull();
		assertThat(response.usage().inputTokens()).isGreaterThan(30);
		assertThat(response.usage().outputTokens()).isGreaterThan(200);

		logger.info("" + response);
	}

	@Test
	public void chatCompletionStream() {
		AnthropicMessage anthropicMessage = new AnthropicMessage(AnthropicMessage.Type.TEXT, "Name 3 famous pirates");
		ChatCompletionMessage chatCompletionMessage = new ChatCompletionMessage(List.of(anthropicMessage), Role.USER);

		AnthropicChatRequest request = AnthropicChatRequest.builder(List.of(chatCompletionMessage))
			.withTemperature(0.8f)
			.withMaxTokens(300)
			.withTopK(10)
			.withAnthropicVersion(DEFAULT_ANTHROPIC_VERSION)
			.build();

		Flux<AnthropicChatBedrockApi.AnthropicChatStreamingResponse> responseStream = anthropicChatApi
			.chatCompletionStream(request);

		List<AnthropicChatBedrockApi.AnthropicChatStreamingResponse> responses = responseStream.collectList().block();
		assertThat(responses).isNotNull();
		assertThat(responses).hasSizeGreaterThan(10);
		assertThat(responses.stream()
			.filter(message -> message.type() == StreamingType.CONTENT_BLOCK_DELTA)
			.map(message -> message.delta().text())
			.collect(Collectors.joining())).contains("Blackbeard");
	}

}
