/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.bedrock.llama.api;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.llama.api.LlamaChatBedrockApi.LlamaChatModel;
import org.springframework.ai.bedrock.llama.api.LlamaChatBedrockApi.LlamaChatRequest;
import org.springframework.ai.bedrock.llama.api.LlamaChatBedrockApi.LlamaChatResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Wei Jiang
 */
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".*")
public class LlamaChatBedrockApiIT {

	private LlamaChatBedrockApi llamaChatApi = new LlamaChatBedrockApi(LlamaChatModel.LLAMA3_70B_INSTRUCT_V1.id(),
			EnvironmentVariableCredentialsProvider.create(), Region.US_EAST_1.id(), new ObjectMapper(),
			Duration.ofMinutes(2));

	@Test
	public void chatCompletion() {

		LlamaChatRequest request = LlamaChatRequest.builder("Hello, my name is")
			.withTemperature(0.9)
			.withTopP(0.9)
			.withMaxGenLen(20)
			.build();

		LlamaChatResponse response = this.llamaChatApi.chatCompletion(request);

		System.out.println(response.generation());
		assertThat(response).isNotNull();
		assertThat(response.generation()).isNotEmpty();
		assertThat(response.generationTokenCount()).isGreaterThan(10);
		assertThat(response.generationTokenCount()).isLessThanOrEqualTo(20);
		assertThat(response.stopReason()).isNotNull();
		assertThat(response.amazonBedrockInvocationMetrics()).isNull();
	}

	@Test
	public void chatCompletionStream() {

		LlamaChatRequest request = new LlamaChatRequest("Hello, my name is", 0.9, 0.9, 20);
		Flux<LlamaChatResponse> responseStream = this.llamaChatApi.chatCompletionStream(request);
		List<LlamaChatResponse> responses = responseStream.collectList().block();

		assertThat(responses).isNotNull();
		assertThat(responses).hasSizeGreaterThan(10);
		assertThat(responses.get(0).generation()).isNotEmpty();

		LlamaChatResponse lastResponse = responses.get(responses.size() - 1);
		assertThat(lastResponse.stopReason()).isNotNull();
		assertThat(lastResponse.amazonBedrockInvocationMetrics()).isNotNull();
	}

}
