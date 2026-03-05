/*
 * Copyright 2023-2026 the original author or authors.
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

package org.springframework.ai.openai;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.api.OpenAiApi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link OpenAiEmbeddingModel}.
 *
 * @author guan xu
 */
class OpenAiEmbeddingModelTests {

	@Test
	void testDefaultModelDimensions() {
		OpenAiApi openAiApi = OpenAiApi.builder().apiKey("sk-123").build();
		OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(openAiApi);
		OpenAiEmbeddingModel spyEmbeddingModel = spy(embeddingModel);
		int dimensions = spyEmbeddingModel.dimensions();
		assertEquals(1536, dimensions);
		verify(spyEmbeddingModel, never()).embed(anyString());
	}

	@Test
	void testCustomModelDimensions() {
		OpenAiApi openAiApi = OpenAiApi.builder().apiKey("sk-123").build();
		OpenAiEmbeddingOptions embeddingOptions = OpenAiEmbeddingOptions.builder()
			.model("custom-text-embedding")
			.build();
		OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, embeddingOptions);
		OpenAiEmbeddingModel spyEmbeddingModel = spy(embeddingModel);
		int dimensions = spyEmbeddingModel.dimensions();
		assertEquals(1024, dimensions);
		verify(spyEmbeddingModel, never()).embed(anyString());
	}

}
