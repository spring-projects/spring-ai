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

package org.springframework.ai.cohere;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.cohere.api.CohereApi;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletion;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionChunk;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionFinishReason;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionMessage.Role;
import org.springframework.ai.cohere.api.CohereApi.ChatCompletionRequest;
import org.springframework.ai.cohere.api.CohereApi.EmbeddingRequest;
import org.springframework.ai.cohere.api.CohereApi.EmbeddingResponse;
import org.springframework.ai.cohere.api.CohereApi.Usage;
import org.springframework.ai.cohere.chat.CohereChatModel;
import org.springframework.ai.cohere.chat.CohereChatOptions;
import org.springframework.ai.cohere.embedding.CohereEmbeddingModel;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;

/**
 * @author Ricken Bazolo
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
public class CohereRetryTests {

	private TestRetryListener retryListener;

	private RetryTemplate retryTemplate;

	private @Mock CohereApi cohereApi;

	private CohereChatModel chatModel;

	private CohereEmbeddingModel embeddingModel;

	@BeforeEach
	public void beforeEach() {
		this.retryTemplate = RetryUtils.SHORT_RETRY_TEMPLATE;
		this.retryListener = new TestRetryListener();
		this.retryTemplate.registerListener(this.retryListener);

		this.chatModel = CohereChatModel.builder()
			.cohereApi(this.cohereApi)
			.defaultOptions(CohereChatOptions.builder()
				.temperature(0.7)
				.topP(1.0)
				.model(CohereApi.ChatModel.COMMAND_A_R7B.getValue())
				.build())
			.retryTemplate(this.retryTemplate)
			.build();
		this.embeddingModel = CohereEmbeddingModel.builder()
			.cohereApi(this.cohereApi)
			.retryTemplate(this.retryTemplate)
			.build();
	}

	@Test
	public void cohereChatTransientError() {
		var message = new ChatCompletionMessage.Provider(
				List.of(new ChatCompletionMessage.MessageContent("text", "Response", null)), Role.ASSISTANT, null, null,
				null);

		ChatCompletion expectedChatCompletion = new ChatCompletion("id", ChatCompletionFinishReason.COMPLETE, message,
				null, new Usage(null, new Usage.Tokens(10, 20), 10));

		given(this.cohereApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
			.willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(ResponseEntity.of(Optional.of(expectedChatCompletion)));

		var result = this.chatModel.call(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getText()).isEqualTo("Response");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void cohereChatNonTransientError() {
		given(this.cohereApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
			.willThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> this.chatModel.call(new Prompt("text")));
	}

	@Test
	@Disabled("Currently stream() does not implement retry")
	public void cohereChatStreamTransientError() {
		var message = new ChatCompletionMessage("Response", Role.ASSISTANT);

		var delta = new ChatCompletionChunk.ChunkDelta(message, ChatCompletionFinishReason.COMPLETE, null);

		ChatCompletionChunk expectedChunk = new ChatCompletionChunk("id", "content-delta", 0, delta);

		given(this.cohereApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
			.willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(Flux.just(expectedChunk));

		var result = this.chatModel.stream(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result.collectList().block().get(0).getResult().getOutput().getText()).isEqualTo("Response");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	@Disabled("Currently stream() does not implement retry")
	public void cohereChatStreamNonTransientError() {
		given(this.cohereApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
			.willThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> this.chatModel.stream(new Prompt("text")));
	}

	@Test
	@Disabled("Embedding tests need to be adapted for Cohere API structure")
	public void cohereEmbeddingTransientError() {
		List<List<Double>> embeddingsList = List.of(List.of(9.9, 8.8), List.of(7.7, 6.6));

		EmbeddingResponse expectedEmbeddings = new EmbeddingResponse("id", embeddingsList, List.of("text1", "text2"),
				"embeddings_floats");

		given(this.cohereApi.embeddings(isA(EmbeddingRequest.class)))
			.willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(ResponseEntity.of(Optional.of(expectedEmbeddings)));

		var result = this.embeddingModel
			.call(new org.springframework.ai.embedding.EmbeddingRequest(List.of("text1", "text2"), null));

		assertThat(result).isNotNull();
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void cohereEmbeddingNonTransientError() {
		given(this.cohereApi.embeddings(isA(EmbeddingRequest.class)))
			.willThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> this.embeddingModel
			.call(new org.springframework.ai.embedding.EmbeddingRequest(List.of("text1", "text2"), null)));
	}

	@Test
	public void cohereChatMixedTransientAndNonTransientErrors() {
		given(this.cohereApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
			.willThrow(new TransientAiException("Transient Error"))
			.willThrow(new RuntimeException("Non Transient Error"));

		// Should fail immediately on non-transient error, no further retries
		assertThrows(RuntimeException.class, () -> this.chatModel.call(new Prompt("text")));

		// Should have 1 retry attempt before hitting non-transient error
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
	}

	private static class TestRetryListener implements RetryListener {

		int onErrorRetryCount = 0;

		int onSuccessRetryCount = 0;

		@Override
		public <T, E extends Throwable> void onSuccess(RetryContext context, RetryCallback<T, E> callback, T result) {
			this.onSuccessRetryCount = context.getRetryCount();
		}

		@Override
		public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
				Throwable throwable) {
			this.onErrorRetryCount = context.getRetryCount();
		}

	}

}
