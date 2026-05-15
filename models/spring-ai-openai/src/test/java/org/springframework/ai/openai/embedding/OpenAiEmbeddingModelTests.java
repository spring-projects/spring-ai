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

package org.springframework.ai.openai.embedding;

import java.util.List;

import com.openai.client.OpenAIClient;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.services.blocking.EmbeddingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Unit tests for {@link OpenAiEmbeddingModel}.
 */
@ExtendWith(MockitoExtension.class)
class OpenAiEmbeddingModelTests {

	@Mock
	OpenAIClient openAiClient;

	@Mock
	EmbeddingService embeddingService;

	@Captor
	ArgumentCaptor<EmbeddingCreateParams> embeddingCreateParamsCaptor;

	@Test
	void requestLevelGenericOptionsOverrideDefaults() {
		given(this.openAiClient.embeddings()).willReturn(this.embeddingService);
		given(this.embeddingService.create(this.embeddingCreateParamsCaptor.capture()))
			.willReturn(CreateEmbeddingResponse.builder()
				.model("text-embedding-3-small")
				.addData(Embedding.builder().embedding(List.of(0.1f, 0.2f, 0.3f)).index(0).build())
				.usage(CreateEmbeddingResponse.Usage.builder().promptTokens(1).totalTokens(1).build())
				.build());

		OpenAiEmbeddingModel embeddingModel = new OpenAiEmbeddingModel(this.openAiClient, null,
				OpenAiEmbeddingOptions.builder().model("text-embedding-ada-002").build());

		embeddingModel.call(new EmbeddingRequest(List.of("Hello World"),
				EmbeddingOptions.builder().model("text-embedding-3-small").dimensions(512).build()));

		EmbeddingCreateParams createParams = this.embeddingCreateParamsCaptor.getValue();
		assertThat(createParams.input().asArrayOfStrings()).containsExactly("Hello World");
		assertThat(createParams.model().asString()).isEqualTo("text-embedding-3-small");
		assertThat(createParams.dimensions()).contains(512L);
	}

}
