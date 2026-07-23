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

import java.time.Duration;
import java.util.List;

import com.openai.client.OpenAIClient;
import com.openai.core.RequestOptions;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpenAiEmbeddingModel}.
 *
 * @author guan xu
 */
class OpenAiEmbeddingModelTests {

	@Test
	void testPropagatesTimeoutFromRequestOptions() {
		Duration expectedTimeout = Duration.ofSeconds(30);

		OpenAIClient mockClient = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
		CreateEmbeddingResponse mockResponse = mock(CreateEmbeddingResponse.class);
		when(mockResponse.data()).thenReturn(List.of());
		when(mockResponse.usage()).thenReturn(mock(CreateEmbeddingResponse.Usage.class));
		when(mockClient.embeddings().create(any(EmbeddingCreateParams.class), any(RequestOptions.class)))
			.thenReturn(mockResponse);

		OpenAiEmbeddingModel model = OpenAiEmbeddingModel.builder().openAiClient(mockClient).build();

		OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder().timeout(expectedTimeout).build();

		model.call(new EmbeddingRequest(List.of("hi"), options));

		ArgumentCaptor<RequestOptions> argumentCaptor = ArgumentCaptor.forClass(RequestOptions.class);
		verify(mockClient.embeddings()).create(any(EmbeddingCreateParams.class), argumentCaptor.capture());
		RequestOptions value = argumentCaptor.getValue();
		assertThat(value.getTimeout()).isNotNull();
		assertThat(value.getTimeout().request()).isEqualTo(expectedTimeout);
	}

}
