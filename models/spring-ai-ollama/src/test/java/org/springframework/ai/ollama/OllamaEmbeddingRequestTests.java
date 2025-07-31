/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.ollama;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Jonghoon Park
 */
public class OllamaEmbeddingRequestTests {

	OllamaEmbeddingModel embeddingModel = OllamaEmbeddingModel.builder()
		.ollamaApi(OllamaApi.builder().build())
		.defaultOptions(OllamaEmbeddingOptions.builder().model("DEFAULT_MODEL").build())
		.build();

	@Test
	public void ollamaEmbeddingRequestDefaultOptions() {
		var embeddingRequest = this.embeddingModel.buildEmbeddingRequest(new EmbeddingRequest(List.of("Hello"), null));
		var ollamaRequest = this.embeddingModel.ollamaEmbeddingRequest(embeddingRequest);

		assertThat(ollamaRequest.model()).isEqualTo("DEFAULT_MODEL");
		assertThat(ollamaRequest.input()).isEqualTo(List.of("Hello"));
	}

	@Test
	public void ollamaEmbeddingRequestRequestOptions() {
		var promptOptions = OllamaEmbeddingOptions.builder()//
			.model("PROMPT_MODEL")//
			.build();

		var embeddingRequest = this.embeddingModel
			.buildEmbeddingRequest(new EmbeddingRequest(List.of("Hello"), promptOptions));
		var ollamaRequest = this.embeddingModel.ollamaEmbeddingRequest(embeddingRequest);

		assertThat(ollamaRequest.model()).isEqualTo("PROMPT_MODEL");
		assertThat(ollamaRequest.input()).isEqualTo(List.of("Hello"));
	}

}
