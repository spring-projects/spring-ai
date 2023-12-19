/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.bedrock.anthropic.api;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.anthropic.api.AnthropicChatBedrockApi.AnthropicChatRequest;
import org.springframework.ai.bedrock.anthropic.api.AnthropicChatBedrockApi.AnthropicChatResponse;
import org.springframework.ai.bedrock.anthropic.api.AnthropicChatBedrockApi.AnthropicChatModel;

import static org.assertj.core.api.Assertions.assertThat;;

/**
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".*")
public class AnthropicChatBedrockApiIT {

	private AnthropicChatBedrockApi anthropicChatApi = new AnthropicChatBedrockApi(AnthropicChatModel.CLAUDE_V2.id(),
			Region.EU_CENTRAL_1.id());

	@Test
	public void chatCompletion() {

		AnthropicChatRequest request = AnthropicChatRequest
			.builder(String.format(AnthropicChatBedrockApi.PROMPT_TEMPLATE, "Name 3 famous pirates"))
			.withTemperature(0.8f)
			.withMaxTokensToSample(300)
			.withTopK(10)
			.build();

		AnthropicChatResponse response = anthropicChatApi.chatCompletion(request);

		System.out.println(response.completion());
		assertThat(response).isNotNull();
		assertThat(response.completion()).isNotEmpty();
		assertThat(response.completion()).contains("Blackbeard");
		assertThat(response.stopReason()).isEqualTo("stop_sequence");
		assertThat(response.stop()).isEqualTo("\n\nHuman:");
		assertThat(response.amazonBedrockInvocationMetrics()).isNull();

		System.out.println(response);
	}

	@Test
	public void chatCompletionStream() {

		AnthropicChatRequest request = AnthropicChatRequest
			.builder(String.format(AnthropicChatBedrockApi.PROMPT_TEMPLATE, "Name 3 famous pirates"))
			.withTemperature(0.8f)
			.withMaxTokensToSample(300)
			.withTopK(10)
			.withStopSequences(List.of("\n\nHuman:"))
			.build();

		Flux<AnthropicChatResponse> responseStream = anthropicChatApi.chatCompletionStream(request);

		List<AnthropicChatResponse> responses = responseStream.collectList().block();
		assertThat(responses).isNotNull();
		assertThat(responses).hasSizeGreaterThan(10);
		assertThat(responses.stream().map(AnthropicChatResponse::completion).collect(Collectors.joining()))
			.contains("Blackbeard");
	}

}
