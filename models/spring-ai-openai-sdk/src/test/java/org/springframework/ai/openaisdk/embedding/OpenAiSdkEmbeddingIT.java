/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.openaisdk.embedding;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.openai.models.embeddings.EmbeddingModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.openaisdk.OpenAiSdkEmbeddingModel;
import org.springframework.ai.openaisdk.OpenAiSdkEmbeddingOptions;
import org.springframework.ai.openaisdk.OpenAiSdkTestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link OpenAiSdkEmbeddingModel}.
 *
 * @author Julien Dubois
 */
@SpringBootTest(classes = OpenAiSdkTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiSdkEmbeddingIT {

	private final Resource resource = new DefaultResourceLoader().getResource("classpath:text_source.txt");

	@Autowired
	private OpenAiSdkEmbeddingModel openAiSdkEmbeddingModel;

	@Test
	void defaultEmbedding() {
		assertThat(this.openAiSdkEmbeddingModel).isNotNull();

		EmbeddingResponse embeddingResponse = this.openAiSdkEmbeddingModel.embedForResponse(List.of("Hello World"));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(1536);
		assertThat(embeddingResponse.getMetadata().getUsage().getTotalTokens()).isEqualTo(2);
		assertThat(embeddingResponse.getMetadata().getUsage().getPromptTokens()).isEqualTo(2);

		assertThat(this.openAiSdkEmbeddingModel.dimensions()).isEqualTo(1536);
		assertThat(embeddingResponse.getMetadata().getModel())
			.contains(OpenAiSdkEmbeddingOptions.DEFAULT_EMBEDDING_MODEL);
	}

	@Test
	void embeddingBatchDocuments() throws Exception {
		assertThat(this.openAiSdkEmbeddingModel).isNotNull();
		List<float[]> embeddings = this.openAiSdkEmbeddingModel.embed(
				List.of(new Document("Hello world"), new Document("Hello Spring"), new Document("Hello Spring AI!")),
				OpenAiSdkEmbeddingOptions.builder().model(EmbeddingModel.TEXT_EMBEDDING_ADA_002.toString()).build(),
				new TokenCountBatchingStrategy());
		assertThat(embeddings.size()).isEqualTo(3);
		embeddings
			.forEach(embedding -> assertThat(embedding.length).isEqualTo(this.openAiSdkEmbeddingModel.dimensions()));
	}

	@Test
	void embeddingBatchDocumentsThatExceedTheLimit() throws Exception {
		assertThat(this.openAiSdkEmbeddingModel).isNotNull();
		String contentAsString = this.resource.getContentAsString(StandardCharsets.UTF_8);
		assertThatThrownBy(() -> this.openAiSdkEmbeddingModel.embed(
				List.of(new Document("Hello World"), new Document(contentAsString)),
				OpenAiSdkEmbeddingOptions.builder().model(EmbeddingModel.TEXT_EMBEDDING_ADA_002.toString()).build(),
				new TokenCountBatchingStrategy()))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void embedding3Large() {

		EmbeddingResponse embeddingResponse = this.openAiSdkEmbeddingModel.call(new EmbeddingRequest(
				List.of("Hello World"),
				OpenAiSdkEmbeddingOptions.builder().model(EmbeddingModel.TEXT_EMBEDDING_3_LARGE.toString()).build()));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(3072);
		assertThat(embeddingResponse.getMetadata().getUsage().getTotalTokens()).isEqualTo(2);
		assertThat(embeddingResponse.getMetadata().getUsage().getPromptTokens()).isEqualTo(2);
		assertThat(embeddingResponse.getMetadata().getModel())
			.isEqualTo(EmbeddingModel.TEXT_EMBEDDING_3_LARGE.toString());
	}

	@Test
	void textEmbeddingAda002() {

		EmbeddingResponse embeddingResponse = this.openAiSdkEmbeddingModel.call(new EmbeddingRequest(
				List.of("Hello World"),
				OpenAiSdkEmbeddingOptions.builder().model(EmbeddingModel.TEXT_EMBEDDING_3_SMALL.toString()).build()));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(1536);

		assertThat(embeddingResponse.getMetadata().getUsage().getTotalTokens()).isEqualTo(2);
		assertThat(embeddingResponse.getMetadata().getUsage().getPromptTokens()).isEqualTo(2);
		assertThat(embeddingResponse.getMetadata().getModel())
			.isEqualTo(EmbeddingModel.TEXT_EMBEDDING_3_SMALL.toString());
	}

}
