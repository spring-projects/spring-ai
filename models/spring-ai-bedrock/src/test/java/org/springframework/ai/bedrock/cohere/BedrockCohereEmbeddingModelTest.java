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

package org.springframework.ai.bedrock.cohere;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingModelResponse;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingRequest;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * Unit tests for {@link BedrockCohereEmbeddingModel}.
 *
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
public class BedrockCohereEmbeddingModelTest {

	@Mock
	private CohereEmbeddingBedrockApi api;

	private BedrockCohereEmbeddingModel model;

	@BeforeEach
	void setUp() {
		this.model = new BedrockCohereEmbeddingModel(this.api,
				BedrockCohereEmbeddingOptions.builder()
					.inputType(CohereEmbeddingRequest.InputType.SEARCH_DOCUMENT)
					.truncate(CohereEmbeddingRequest.Truncate.NONE)
					.build());
	}

	@Test
	void shouldReturnTokenUsageFromBedrockInputTokenCountHeader() {
		given(this.api.embeddingForModel(any(CohereEmbeddingRequest.class)))
			.willReturn(new CohereEmbeddingModelResponse(new CohereEmbeddingResponse("id",
					List.of(new float[] { 0.1f, 0.2f }), List.of("Hello"), "embeddings", null), 743));

		EmbeddingResponse response = this.model.call(new EmbeddingRequest(List.of("Hello"), null));

		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getOutput()).hasSize(2);
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(743);
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isZero();
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(743);
	}

	@Test
	void shouldReturnZeroTokenUsageWhenHeaderIsMissing() {
		given(this.api.embeddingForModel(any(CohereEmbeddingRequest.class)))
			.willReturn(new CohereEmbeddingModelResponse(new CohereEmbeddingResponse("id",
					List.of(new float[] { 0.1f, 0.2f }), List.of("Hello"), "embeddings", null), null));

		EmbeddingResponse response = this.model.call(new EmbeddingRequest(List.of("Hello"), null));

		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isZero();
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isZero();
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isZero();
	}

	@Test
	void shouldReturnBatchEmbeddingsWithTokenUsage() {
		given(this.api.embeddingForModel(any(CohereEmbeddingRequest.class)))
			.willReturn(new CohereEmbeddingModelResponse(
					new CohereEmbeddingResponse("id", List.of(new float[] { 0.1f, 0.2f }, new float[] { 0.3f, 0.4f }),
							List.of("Hello", "World"), "embeddings", null),
					25));

		EmbeddingResponse response = this.model.call(new EmbeddingRequest(List.of("Hello", "World"), null));

		assertThat(response.getResults()).hasSize(2);
		assertThat(response.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(response.getResults().get(1).getIndex()).isEqualTo(1);
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isEqualTo(25);
		assertThat(response.getMetadata().getUsage().getCompletionTokens()).isZero();
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(25);
	}

}
