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
import com.openai.core.JsonField;
import com.openai.core.JsonMissing;
import com.openai.core.RequestOptions;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
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
		doReturn(JsonField.of(usage(0L, 0L))).when(mockResponse)._usage();
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

	@Test
	void indexFallsBackToPositionWhenProviderOmitsIt() {
		com.openai.models.embeddings.Embedding e0 = mock(com.openai.models.embeddings.Embedding.class);
		com.openai.models.embeddings.Embedding e1 = mock(com.openai.models.embeddings.Embedding.class);
		when(e0.embedding()).thenReturn(List.of(1.0f, 2.0f));
		when(e1.embedding()).thenReturn(List.of(3.0f, 4.0f));
		// Gemini's OpenAI compatibility mode omits the redundant `index` field.
		when(e0._index()).thenReturn(JsonMissing.of());
		when(e1._index()).thenReturn(JsonField.of(1L));

		OpenAIClient mockClient = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
		CreateEmbeddingResponse mockResponse = mock(CreateEmbeddingResponse.class);
		when(mockResponse.data()).thenReturn(List.of(e0, e1));
		when(mockResponse._usage())
			.thenReturn(JsonField.of(CreateEmbeddingResponse.Usage.builder().promptTokens(1L).totalTokens(1L).build()));
		when(mockClient.embeddings().create(any(EmbeddingCreateParams.class), any(RequestOptions.class)))
			.thenReturn(mockResponse);

		OpenAiEmbeddingModel model = OpenAiEmbeddingModel.builder().openAiClient(mockClient).build();

		var response = model
			.call(new EmbeddingRequest(List.of("One", "Two"), OpenAiEmbeddingOptions.builder().build()));
		assertThat(response.getResults().get(0).getIndex()).isEqualTo(0);
		assertThat(response.getResults().get(1)).isNotNull();
		assertThat(response.getResults().get(1).getIndex()).isEqualTo(1);
	}

	@Test
	void usageCountsBeyondIntRangeFailClearlyInsteadOfBareArithmeticException() {
		OpenAIClient mockClient = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
		// An out-of-range usage count means the upstream response can't be trusted, so
		// it should fail clearly instead of propagating a bare
		// ArithmeticException("integer overflow").
		long promptTokens = Integer.MAX_VALUE + 1L;
		CreateEmbeddingResponse response = CreateEmbeddingResponse.builder()
			.data(List.of())
			.model("test-model")
			.usage(CreateEmbeddingResponse.Usage.builder().promptTokens(promptTokens).totalTokens(promptTokens).build())
			.build();
		when(mockClient.embeddings().create(any(EmbeddingCreateParams.class), any(RequestOptions.class)))
			.thenReturn(response);

		OpenAiEmbeddingModel model = OpenAiEmbeddingModel.builder().openAiClient(mockClient).build();
		EmbeddingRequest request = new EmbeddingRequest(List.of("hi"), OpenAiEmbeddingOptions.builder().build());

		assertThatThrownBy(() -> model.call(request)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("promptTokens")
			.hasMessageContaining(String.valueOf(promptTokens))
			.hasCauseInstanceOf(ArithmeticException.class);
	}

	@Test
	void testEmbeddingWithoutUsageDoesNotFail() {
		OpenAIClient mockClient = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
		CreateEmbeddingResponse mockResponse = mock(CreateEmbeddingResponse.class);
		when(mockResponse.data()).thenReturn(List.of());
		when(mockResponse._usage()).thenReturn(JsonMissing.of());
		when(mockClient.embeddings().create(any(EmbeddingCreateParams.class), any(RequestOptions.class)))
			.thenReturn(mockResponse);

		OpenAiEmbeddingModel model = OpenAiEmbeddingModel.builder().openAiClient(mockClient).build();

		EmbeddingResponse response = model
			.call(new EmbeddingRequest(List.of("hi"), OpenAiEmbeddingOptions.builder().build()));

		assertThat(response).isNotNull();
		// Gemini's OpenAI-compatible embeddings responses may omit usage; no NPE should
		// be thrown
		assertThat(response.getMetadata().getUsage().getPromptTokens()).isNotNull();
	}

	@Test
	void usageWithPartialCountsDoesNotFail() {
		OpenAIClient mockClient = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
		// Some OpenAI-compatible providers report usage with only part of the counts
		// present, e.g. `{"prompt_tokens": 8}` with no `total_tokens`. The required
		// accessors throw OpenAIInvalidDataException for the absent one.
		CreateEmbeddingResponse mockResponse = mock(CreateEmbeddingResponse.class);
		when(mockResponse.data()).thenReturn(List.of());
		doReturn(JsonField.of(usage(8L, null))).when(mockResponse)._usage();
		when(mockClient.embeddings().create(any(EmbeddingCreateParams.class), any(RequestOptions.class)))
			.thenReturn(mockResponse);

		OpenAiEmbeddingModel model = OpenAiEmbeddingModel.builder().openAiClient(mockClient).build();
		EmbeddingRequest request = new EmbeddingRequest(List.of("hi"), OpenAiEmbeddingOptions.builder().build());

		Usage usage = model.call(request).getMetadata().getUsage();
		assertThat(usage.getPromptTokens()).isEqualTo(8);
		assertThat(usage.getCompletionTokens()).isZero();
		// DefaultUsage derives a missing total from the other counts
		assertThat(usage.getTotalTokens()).isEqualTo(8);
	}

	@Test
	void usageWithoutAnyCountsDoesNotFail() {
		OpenAIClient mockClient = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
		// A provider may report an empty `usage` object; every count is then absent.
		CreateEmbeddingResponse mockResponse = mock(CreateEmbeddingResponse.class);
		when(mockResponse.data()).thenReturn(List.of());
		doReturn(JsonField.of(usage(null, null))).when(mockResponse)._usage();
		when(mockClient.embeddings().create(any(EmbeddingCreateParams.class), any(RequestOptions.class)))
			.thenReturn(mockResponse);

		OpenAiEmbeddingModel model = OpenAiEmbeddingModel.builder().openAiClient(mockClient).build();
		EmbeddingRequest request = new EmbeddingRequest(List.of("hi"), OpenAiEmbeddingOptions.builder().build());

		Usage usage = model.call(request).getMetadata().getUsage();
		assertThat(usage.getPromptTokens()).isZero();
		assertThat(usage.getCompletionTokens()).isZero();
		assertThat(usage.getTotalTokens()).isZero();
	}

	/**
	 * A usage object whose raw counts are present or missing independently. The
	 * openai-java builder rejects an absent required count, so the absent case is
	 * expressed here.
	 * @param promptTokens the {@code prompt_tokens} count, or {@code null} to leave it
	 * absent
	 * @param totalTokens the {@code total_tokens} count, or {@code null} to leave it
	 * absent
	 * @return the stubbed usage object
	 */
	private static CreateEmbeddingResponse.Usage usage(Long promptTokens, Long totalTokens) {
		CreateEmbeddingResponse.Usage nativeUsage = mock(CreateEmbeddingResponse.Usage.class);
		doReturn(promptTokens != null ? JsonField.of(promptTokens) : JsonMissing.of()).when(nativeUsage)
			._promptTokens();
		doReturn(totalTokens != null ? JsonField.of(totalTokens) : JsonMissing.of()).when(nativeUsage)._totalTokens();
		return nativeUsage;
	}

}
