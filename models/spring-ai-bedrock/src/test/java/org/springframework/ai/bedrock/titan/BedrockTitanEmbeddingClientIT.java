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
package org.springframework.ai.bedrock.titan;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi;
import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi.TitanEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "AWS_ACCESS_KEY_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".*")
class BedrockTitanEmbeddingClientIT {

	@Autowired
	private BedrockTitanEmbeddingClient embeddingClient;

	@Test
	void singleEmbedding() {
		assertThat(embeddingClient).isNotNull();
		EmbeddingResponse embeddingResponse = embeddingClient.embedForResponse(List.of("Hello World"));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(embeddingClient.dimensions()).isEqualTo(1024);
	}

	@Test
	void imageEmbedding() throws IOException {

		byte[] image = new DefaultResourceLoader().getResource("classpath:/spring_framework.png")
			.getContentAsByteArray();

		EmbeddingResponse embeddingResponse = embeddingClient
			.embedForResponse(List.of(Base64.getEncoder().encodeToString(image)));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(embeddingClient.dimensions()).isEqualTo(1024);
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public TitanEmbeddingBedrockApi titanEmbeddingApi() {
			return new TitanEmbeddingBedrockApi(TitanEmbeddingModel.TITAN_EMBED_IMAGE_V1.id(), Region.US_EAST_1.id(),
					Duration.ofMinutes(2));
		}

		@Bean
		public BedrockTitanEmbeddingClient titanEmbedding(TitanEmbeddingBedrockApi titanEmbeddingApi) {
			return new BedrockTitanEmbeddingClient(titanEmbeddingApi);
		}

	}

}
