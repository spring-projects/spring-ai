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
package org.springframework.ai.bedrock.titan.metadata;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.bedrock.titan.BedrockTitanChatClient;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi;
import org.springframework.ai.bedrock.titan.api.TitanChatBedrockApi.TitanChatModel;
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
public class BedrockTitanChatResponseMetadataT {

	@Autowired
	private BedrockTitanChatClient client;

	@Test
	public void chatCompletion() {
		Prompt prompt = new Prompt("Can I fly like a bird?");

		ChatResponse response = this.client.call(prompt);

		assertThat(response).isNotNull();
		assertThat(response.getMetadata()).isNotNull();
		assertThat(response.getMetadata()).isInstanceOf(BedrockTitanChatResponseMetadata.class);

		BedrockTitanChatResponseMetadata metadata = (BedrockTitanChatResponseMetadata) response.getMetadata();
		assertThat(metadata.getId()).isNotNull();
		assertThat(metadata.getInvocationLatency()).isNotNull();

		assertThat(response.getMetadata().getUsage()).isNotNull();
		assertThat(response.getMetadata().getUsage().getGenerationTokens()).isGreaterThan(0);
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isGreaterThan(0);
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public TitanChatBedrockApi titanApi() {
			return new TitanChatBedrockApi(TitanChatModel.TITAN_TEXT_EXPRESS_V1.id(),
					EnvironmentVariableCredentialsProvider.create(), Region.US_EAST_1.id(), new ObjectMapper(),
					Duration.ofMinutes(2));
		}

		@Bean
		public BedrockTitanChatClient titanChatClient(TitanChatBedrockApi titanApi) {
			return new BedrockTitanChatClient(titanApi);
		}

	}

}
