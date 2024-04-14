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
package org.springframework.ai.bedrock.cohere;

import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingModel;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingRequest.InputType;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".*")
class BedrockCohereEmbeddingClientIT {

	@Autowired
	private BedrockCohereEmbeddingClient embeddingClient;

	@Test
	void singleEmbedding() {
		assertThat(embeddingClient).isNotNull();
		EmbeddingResponse embeddingResponse = embeddingClient.embedForResponse(List.of("Hello World"));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(embeddingClient.dimensions()).isEqualTo(1024);
	}

	@Test
	void batchEmbedding() {
		assertThat(embeddingClient).isNotNull();
		EmbeddingResponse embeddingResponse = embeddingClient
			.embedForResponse(List.of("Hello World", "World is big and salvation is near"));
		assertThat(embeddingResponse.getResults()).hasSize(2);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);

		assertThat(embeddingClient.dimensions()).isEqualTo(1024);
	}

	@Test
	void embeddingWthOptions() {
		assertThat(embeddingClient).isNotNull();
		EmbeddingResponse embeddingResponse = embeddingClient
			.call(new EmbeddingRequest(List.of("Hello World", "World is big and salvation is near"),
					BedrockCohereEmbeddingOptions.builder().withInputType(InputType.SEARCH_DOCUMENT).build()));
		assertThat(embeddingResponse.getResults()).hasSize(2);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);

		assertThat(embeddingClient.dimensions()).isEqualTo(1024);
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public CohereEmbeddingBedrockApi cohereEmbeddingApi() {
			return new CohereEmbeddingBedrockApi(CohereEmbeddingModel.COHERE_EMBED_MULTILINGUAL_V1.id(),
					EnvironmentVariableCredentialsProvider.create(), Region.US_EAST_1.id(), new ObjectMapper(),
					Duration.ofMinutes(2));
		}

		@Bean
		public BedrockCohereEmbeddingClient cohereAiEmbedding(CohereEmbeddingBedrockApi cohereEmbeddingApi) {
			return new BedrockCohereEmbeddingClient(cohereEmbeddingApi);
		}

	}

}
