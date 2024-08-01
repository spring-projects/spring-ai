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
package org.springframework.ai.openai.embedding;

import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.OpenAiTestConfiguration;
import org.springframework.ai.openai.testutils.AbstractIT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = OpenAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class EmbeddingIT extends AbstractIT {

	@Autowired
	private OpenAiEmbeddingModel embeddingModel;

	@Test
	void defaultEmbedding() {
		assertThat(embeddingModel).isNotNull();

		EmbeddingResponse embeddingResponse = embeddingModel.embedForResponse(List.of("Hello World"));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(1536);
		assertThat(embeddingResponse.getMetadata().getModel()).isEqualTo("text-embedding-ada-002");
		assertThat(embeddingResponse.getMetadata().getUsage().getTotalTokens()).isEqualTo(2);
		assertThat(embeddingResponse.getMetadata().getUsage().getPromptTokens()).isEqualTo(2);

		assertThat(embeddingModel.dimensions()).isEqualTo(1536);
	}

	@Test
	void embedding3Large() {

		EmbeddingResponse embeddingResponse = embeddingModel.call(new EmbeddingRequest(List.of("Hello World"),
				OpenAiEmbeddingOptions.builder().withModel("text-embedding-3-large").build()));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(3072);
		assertThat(embeddingResponse.getMetadata().getModel()).isEqualTo("text-embedding-3-large");
		assertThat(embeddingResponse.getMetadata().getUsage().getTotalTokens()).isEqualTo(2);
		assertThat(embeddingResponse.getMetadata().getUsage().getPromptTokens()).isEqualTo(2);

		// assertThat(embeddingModel.dimensions()).isEqualTo(3072);
	}

	@Test
	void textEmbeddingAda002() {

		EmbeddingResponse embeddingResponse = embeddingModel.call(new EmbeddingRequest(List.of("Hello World"),
				OpenAiEmbeddingOptions.builder().withModel("text-embedding-3-small").build()));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(1536);

		assertThat(embeddingResponse.getMetadata().getModel()).isEqualTo("text-embedding-3-small");
		assertThat(embeddingResponse.getMetadata().getUsage().getTotalTokens()).isEqualTo(2);
		assertThat(embeddingResponse.getMetadata().getUsage().getPromptTokens()).isEqualTo(2);

		// assertThat(embeddingModel.dimensions()).isEqualTo(3072);
	}

}
