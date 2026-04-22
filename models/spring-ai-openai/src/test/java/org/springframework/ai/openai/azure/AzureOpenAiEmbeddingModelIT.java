/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.openai.azure;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariables({ @EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_API_KEY", matches = ".+"),
		@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_ENDPOINT", matches = ".+") })
class AzureOpenAiEmbeddingModelIT {

	@Autowired
	private OpenAiEmbeddingModel embeddingModel;

	@Test
	void singleEmbedding() {
		assertThat(this.embeddingModel).isNotNull();
		EmbeddingResponse embeddingResponse = this.embeddingModel.embedForResponse(List.of("Hello World"));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		System.out.println(this.embeddingModel.dimensions());
		assertThat(this.embeddingModel.dimensions()).isEqualTo(1536);
	}

	@Test
	void batchEmbedding() {
		assertThat(this.embeddingModel).isNotNull();
		EmbeddingResponse embeddingResponse = this.embeddingModel
			.embedForResponse(List.of("Hello World", "World is big and salvation is near"));
		assertThat(embeddingResponse.getResults()).hasSize(2);
		assertThat(embeddingResponse.getResults().get(0).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(embeddingResponse.getResults().get(1).getOutput()).isNotEmpty();
		assertThat(embeddingResponse.getResults().get(1).getIndex()).isEqualTo(1);

		assertThat(this.embeddingModel.dimensions()).isEqualTo(1536);
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public OpenAiEmbeddingModel azureEmbeddingModel() {
			return new OpenAiEmbeddingModel(MetadataMode.EMBED,
					OpenAiEmbeddingOptions.builder()
						.baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
						.apiKey(System.getenv("AZURE_OPENAI_API_KEY"))
						.deploymentName("text-embedding-ada-002")
						.build());
		}

	}

}
