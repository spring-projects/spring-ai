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

package org.springframework.ai.azure.openai;

import java.util.List;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@RequiresAzureCredentials
class AzureOpenAiEmbeddingModelIT {

	@Autowired
	private AzureOpenAiEmbeddingModel embeddingModel;

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
		public OpenAIClient openAIClient() {
			return new OpenAIClientBuilder().credential(new AzureKeyCredential(System.getenv("AZURE_OPENAI_API_KEY")))
				.endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
				.buildClient();
		}

		@Bean
		public AzureOpenAiEmbeddingModel azureEmbeddingModel(OpenAIClient openAIClient) {
			return new AzureOpenAiEmbeddingModel(openAIClient, MetadataMode.EMBED,
					AzureOpenAiEmbeddingOptions.builder().withDeploymentName("text-embedding-ada-002").build());
		}

	}

}
