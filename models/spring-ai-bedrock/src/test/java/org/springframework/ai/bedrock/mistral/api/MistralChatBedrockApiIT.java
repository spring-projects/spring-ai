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
package org.springframework.ai.bedrock.mistral.api;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.bedrock.mistral.api.MistralChatBedrockApi.MistralChatRequest;
import org.springframework.ai.bedrock.mistral.api.MistralChatBedrockApi.MistralChatResponse;
import org.springframework.ai.bedrock.mistral.api.MistralChatBedrockApi.MistralChatModel;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Wei Jiang
 * @since 1.0.0
 */
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".*")
public class MistralChatBedrockApiIT {

	private MistralChatBedrockApi mistralChatApi = new MistralChatBedrockApi(
			MistralChatModel.MISTRAL_8X7B_INSTRUCT.id(), EnvironmentVariableCredentialsProvider.create(),
			Region.US_EAST_1.id(), new ObjectMapper(), Duration.ofMinutes(2));

	@Test
	public void chatCompletion() {

		MistralChatRequest request = MistralChatRequest.builder("Hello, Who are you?")
			.withTemperature(0.9f)
			.withTopP(0.9f)
			.build();

		MistralChatResponse response = mistralChatApi.chatCompletion(request);

		assertThat(response).isNotNull();
		assertThat(response.outputs()).isNotEmpty();
		assertThat(response.outputs().get(0)).isNotNull();
		assertThat(response.outputs().get(0).text()).isNotNull();
		assertThat(response.outputs().get(0).stopReason()).isNotNull();
	}

	@Test
	public void chatCompletionStream() {

		MistralChatRequest request = MistralChatRequest.builder("Hello, Who are you?")
			.withTemperature(0.9f)
			.withTopP(0.9f)
			.build();
		Flux<MistralChatResponse> responseStream = mistralChatApi.chatCompletionStream(request);
		List<MistralChatResponse> responses = responseStream.collectList().block();

		assertThat(responses).isNotNull();
		assertThat(responses).hasSizeGreaterThan(10);
		assertThat(responses.get(0).outputs()).isNotEmpty();

		MistralChatResponse lastResponse = responses.get(responses.size() - 1);
		assertThat(lastResponse.amazonBedrockInvocationMetrics()).isNotNull();
	}

}
