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

package org.springframework.ai.mistralai;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = MistralAiTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "MISTRAL_AI_API_KEY", matches = ".+")
class MistralAiEmbeddingIT {

	@Autowired
	private MistralAiEmbeddingModel mistralAiEmbeddingModel;

	@Test
	void defaultEmbedding() {
		assertThat(this.mistralAiEmbeddingModel).isNotNull();
		var embeddingResponse = this.mistralAiEmbeddingModel.embedForResponse(List.of("Hello World"));
		assertThat(embeddingResponse.getResults()).hasSize(1);
		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(1024);
		assertThat(embeddingResponse.getMetadata().getModel()).isEqualTo("mistral-embed");
		assertThat(embeddingResponse.getMetadata().getUsage().getTotalTokens()).isEqualTo(4);
		assertThat(embeddingResponse.getMetadata().getUsage().getPromptTokens()).isEqualTo(4);
		assertThat(this.mistralAiEmbeddingModel.dimensions()).isEqualTo(1024);
	}

	@Test
	void embeddingTest() {
		assertThat(this.mistralAiEmbeddingModel).isNotNull();
		var embeddingResponse = this.mistralAiEmbeddingModel.call(new EmbeddingRequest(
				List.of("Hello World", "World is big"),
				MistralAiEmbeddingOptions.builder().withModel("mistral-embed").withEncodingFormat("float").build()));
		assertThat(embeddingResponse.getResults()).hasSize(2);
		assertThat(embeddingResponse.getResults().get(0)).isNotNull();
		assertThat(embeddingResponse.getResults().get(0).getOutput()).hasSize(1024);
		assertThat(embeddingResponse.getMetadata().getModel()).isEqualTo("mistral-embed");
		assertThat(embeddingResponse.getMetadata().getUsage().getTotalTokens()).isEqualTo(9);
		assertThat(embeddingResponse.getMetadata().getUsage().getPromptTokens()).isEqualTo(9);
		assertThat(this.mistralAiEmbeddingModel.dimensions()).isEqualTo(1024);
	}

}
