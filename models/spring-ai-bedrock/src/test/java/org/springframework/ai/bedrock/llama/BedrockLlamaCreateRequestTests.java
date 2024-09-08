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
package org.springframework.ai.bedrock.llama;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.llama.api.LlamaChatBedrockApi;
import org.springframework.ai.bedrock.llama.api.LlamaChatBedrockApi.LlamaChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Wei Jiang
 */
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".*")
public class BedrockLlamaCreateRequestTests {

	private LlamaChatBedrockApi api = new LlamaChatBedrockApi(LlamaChatModel.LLAMA3_70B_INSTRUCT_V1.id(),
			EnvironmentVariableCredentialsProvider.create(), Region.US_EAST_1.id(), new ObjectMapper(),
			Duration.ofMinutes(2));

	@Test
	public void createRequestWithChatOptions() {

		var client = new BedrockLlamaChatModel(api,
				BedrockLlamaChatOptions.builder().withTemperature(66.6).withMaxGenLen(666).withTopP(0.66).build());

		var request = client.createRequest(new Prompt("Test message content"));

		assertThat(request.prompt()).isNotEmpty();
		assertThat(request.temperature()).isEqualTo(66.6f);
		assertThat(request.topP()).isEqualTo(0.66f);
		assertThat(request.maxGenLen()).isEqualTo(666);

		request = client.createRequest(new Prompt("Test message content",
				BedrockLlamaChatOptions.builder().withTemperature(99.9).withMaxGenLen(999).withTopP(0.99).build()));

		assertThat(request.prompt()).isNotEmpty();
		assertThat(request.temperature()).isEqualTo(99.9f);
		assertThat(request.topP()).isEqualTo(0.99f);
		assertThat(request.maxGenLen()).isEqualTo(999);
	}

}
