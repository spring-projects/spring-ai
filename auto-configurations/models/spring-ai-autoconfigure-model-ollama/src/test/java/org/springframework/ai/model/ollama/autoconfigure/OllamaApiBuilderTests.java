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

package org.springframework.ai.model.ollama.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.api.OllamaApi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

/**
 * @author lambochen
 */
public class OllamaApiBuilderTests {

	@Test
	void fullBuilder() {
		OllamaApi ollamaApi = OllamaApi.builder()
			.baseUrl("http://localhost:11434")
			.chatPath("/api/chat")
			.embedPath("/api/embeddings")
			.listModelsPath("/api/tags")
			.showModelPath("/api/show")
			.copyModelPath("/api/copy")
			.deleteModelPath("/api/delete")
			.pullModelPath("/api/pull")
			.build();

		assertThat(ollamaApi).isNotNull();
	}

	@Test
	void defaultBuilder() {
		OllamaApi ollamaApi = OllamaApi.builder().build();
		assertThat(ollamaApi).isNotNull();
	}

	@Test
	void invalidPath() {
		assertThatThrownBy(() -> OllamaApi.builder().chatPath("").build()).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> OllamaApi.builder().embedPath("").build())
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> OllamaApi.builder().listModelsPath("").build())
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> OllamaApi.builder().showModelPath("").build())
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> OllamaApi.builder().copyModelPath("").build())
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> OllamaApi.builder().deleteModelPath("").build())
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> OllamaApi.builder().pullModelPath("").build())
			.isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> OllamaApi.builder().chatPath(null).build())
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> OllamaApi.builder().embedPath(null).build())
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> OllamaApi.builder().listModelsPath(null).build())
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> OllamaApi.builder().showModelPath(null).build())
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> OllamaApi.builder().copyModelPath(null).build())
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> OllamaApi.builder().deleteModelPath(null).build())
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> OllamaApi.builder().pullModelPath(null).build())
			.isInstanceOf(IllegalArgumentException.class);
	}

}
