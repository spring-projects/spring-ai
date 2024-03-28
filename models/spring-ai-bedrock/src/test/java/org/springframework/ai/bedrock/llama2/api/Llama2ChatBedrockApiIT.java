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
package org.springframework.ai.bedrock.llama2.api;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.llama2.api.Llama2ChatBedrockApi.Llama2ChatModel;
import org.springframework.ai.bedrock.llama2.api.Llama2ChatBedrockApi.Llama2ChatRequest;
import org.springframework.ai.bedrock.llama2.api.Llama2ChatBedrockApi.Llama2ChatResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".*")
public class Llama2ChatBedrockApiIT {

	private Llama2ChatBedrockApi llama2ChatApi = new Llama2ChatBedrockApi(Llama2ChatModel.LLAMA2_70B_CHAT_V1.id(),
			Region.US_EAST_1.id(), Duration.ofMinutes(2));

	@Test
	public void chatCompletion() {

		Llama2ChatRequest request = Llama2ChatRequest.builder("Hello, my name is")
			.withTemperature(0.9f)
			.withTopP(0.9f)
			.withMaxGenLen(20)
			.build();

		Llama2ChatResponse response = llama2ChatApi.chatCompletion(request);

		System.out.println(response.generation());
		assertThat(response).isNotNull();
		assertThat(response.generation()).isNotEmpty();
		assertThat(response.promptTokenCount()).isEqualTo(6);
		assertThat(response.generationTokenCount()).isGreaterThan(10);
		assertThat(response.generationTokenCount()).isLessThanOrEqualTo(20);
		assertThat(response.stopReason()).isNotNull();
		assertThat(response.amazonBedrockInvocationMetrics()).isNull();
	}

	@Test
	public void chatCompletionStream() {

		Llama2ChatRequest request = new Llama2ChatRequest("Hello, my name is", 0.9f, 0.9f, 20);
		Flux<Llama2ChatResponse> responseStream = llama2ChatApi.chatCompletionStream(request);
		List<Llama2ChatResponse> responses = responseStream.collectList().block();

		assertThat(responses).isNotNull();
		assertThat(responses).hasSizeGreaterThan(10);
		assertThat(responses.get(0).generation()).isNotEmpty();

		Llama2ChatResponse lastResponse = responses.get(responses.size() - 1);
		assertThat(lastResponse.stopReason()).isNotNull();
		assertThat(lastResponse.amazonBedrockInvocationMetrics()).isNotNull();
	}

}
