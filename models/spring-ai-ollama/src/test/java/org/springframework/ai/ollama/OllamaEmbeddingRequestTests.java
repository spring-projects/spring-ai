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
package org.springframework.ai.ollama;

import org.junit.jupiter.api.Test;

import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class OllamaEmbeddingRequestTests {

	OllamaEmbeddingClient client = new OllamaEmbeddingClient(new OllamaApi()).withDefaultOptions(
			new OllamaOptions().withModel("DEFAULT_MODEL").withMainGPU(11).withUseMMap(true).withNumGPU(1));

	@Test
	public void ollamaEmbeddingRequestDefaultOptions() {

		var request = client.ollamaEmbeddingRequest("Hello", null);

		assertThat(request.model()).isEqualTo("DEFAULT_MODEL");
		assertThat(request.options().get("num_gpu")).isEqualTo(1);
		assertThat(request.options().get("main_gpu")).isEqualTo(11);
		assertThat(request.options().get("use_mmap")).isEqualTo(true);
		assertThat(request.prompt()).isEqualTo("Hello");
	}

	@Test
	public void ollamaEmbeddingRequestRequestOptions() {

		EmbeddingOptions promptOptions = new OllamaOptions().withModel("PROMPT_MODEL")
			.withMainGPU(22)
			.withUseMMap(true)
			.withNumGPU(2);

		var request = client.ollamaEmbeddingRequest("Hello", promptOptions);

		assertThat(request.model()).isEqualTo("PROMPT_MODEL");
		assertThat(request.options().get("num_gpu")).isEqualTo(2);
		assertThat(request.options().get("main_gpu")).isEqualTo(22);
		assertThat(request.options().get("use_mmap")).isEqualTo(true);
		assertThat(request.prompt()).isEqualTo("Hello");
	}

}
