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
package org.springframework.ai.bedrock.jurassic2.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.bedrock.jurassic2.BedrockAi21Jurassic2ChatClient;
import org.springframework.ai.bedrock.jurassic2.BedrockAi21Jurassic2ChatOptions;
import org.springframework.ai.bedrock.jurassic2.api.Ai21Jurassic2ChatBedrockApi;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;

/**
 * @author Wei Jiang
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".*")
public class BedrockAi21Jurassic2ChatResponseMetadataT {

	@Autowired
	private BedrockAi21Jurassic2ChatClient client;

	@Test
	public void chatCompletion() {
		Prompt prompt = new Prompt("Can I fly like a bird?");

		ChatResponse response = this.client.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getMetadata()).isNotNull();
		assertThat(response.getMetadata()).isInstanceOf(BedrockAi21Jurassic2ChatResponseMetadata.class);

		BedrockAi21Jurassic2ChatResponseMetadata metadata = (BedrockAi21Jurassic2ChatResponseMetadata) response
			.getMetadata();
		assertThat(metadata.getId()).isNotNull();
		assertThat(metadata.getInvocationLatency()).isNotNull();

		assertThat(response.getMetadata().getUsage()).isNotNull();
		assertThat(response.getMetadata().getUsage().getGenerationTokens()).isGreaterThan(0);
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isGreaterThan(0);
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public Ai21Jurassic2ChatBedrockApi jurassic2ChatBedrockApi() {
			return new Ai21Jurassic2ChatBedrockApi(
					Ai21Jurassic2ChatBedrockApi.Ai21Jurassic2ChatModel.AI21_J2_MID_V1.id(),
					EnvironmentVariableCredentialsProvider.create(), Region.US_EAST_1.id(), new ObjectMapper(),
					Duration.ofMinutes(2));
		}

		@Bean
		public BedrockAi21Jurassic2ChatClient bedrockAi21Jurassic2ChatClient(
				Ai21Jurassic2ChatBedrockApi jurassic2ChatBedrockApi) {
			return new BedrockAi21Jurassic2ChatClient(jurassic2ChatBedrockApi,
					BedrockAi21Jurassic2ChatOptions.builder()
						.withTemperature(0.5f)
						.withMaxTokens(100)
						.withTopP(0.9f)
						.build());
		}

	}

}
