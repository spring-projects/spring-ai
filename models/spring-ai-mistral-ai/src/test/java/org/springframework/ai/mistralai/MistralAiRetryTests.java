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

package org.springframework.ai.mistralai;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletion;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionChunk;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionFinishReason;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.mistralai.api.MistralAiApi.ChatCompletionRequest;
import org.springframework.ai.mistralai.api.MistralAiApi.Embedding;
import org.springframework.ai.mistralai.api.MistralAiApi.EmbeddingList;
import org.springframework.ai.mistralai.api.MistralAiApi.EmbeddingRequest;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Alexandros Pappas
 * @author Jason Smith
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
public class MistralAiRetryTests {

	private TestRetryListener retryListener;

	private RetryTemplate retryTemplate;

	private @Mock MistralAiApi mistralAiApi;

	private MistralAiChatModel chatModel;

	private MistralAiEmbeddingModel embeddingModel;

	@BeforeEach
	public void beforeEach() {
		this.retryTemplate = RetryUtils.SHORT_RETRY_TEMPLATE;
		this.retryListener = new TestRetryListener();
		this.retryTemplate.registerListener(this.retryListener);

		this.chatModel = MistralAiChatModel.builder()
			.mistralAiApi(this.mistralAiApi)
			.defaultOptions(MistralAiChatOptions.builder()
				.temperature(0.7)
				.topP(1.0)
				.safePrompt(false)
				.model(MistralAiApi.ChatModel.MISTRAL_SMALL.getValue())
				.build())
			.retryTemplate(this.retryTemplate)
			.build();
		this.embeddingModel = MistralAiEmbeddingModel.builder()
			.mistralAiApi(this.mistralAiApi)
			.retryTemplate(this.retryTemplate)
			.build();
	}

	@Test
	public void mistralAiChatTransientError() {

		var choice = new ChatCompletion.Choice(0, new ChatCompletionMessage("Response", Role.ASSISTANT),
				ChatCompletionFinishReason.STOP, null);
		ChatCompletion expectedChatCompletion = new ChatCompletion("id", "chat.completion", 789L, "model",
				List.of(choice), new MistralAiApi.Usage(10, 10, 10));

		given(this.mistralAiApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
			.willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(ResponseEntity.of(Optional.of(expectedChatCompletion)));

		var result = this.chatModel.call(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getText()).isSameAs("Response");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void mistralAiChatNonTransientError() {
		given(this.mistralAiApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
			.willThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> this.chatModel.call(new Prompt("text")));
	}

	@Test
	@Disabled("Currently stream() does not implement retry")
	public void mistralAiChatStreamTransientError() {

		var choice = new ChatCompletionChunk.ChunkChoice(0, new ChatCompletionMessage("Response", Role.ASSISTANT),
				ChatCompletionFinishReason.STOP, null);
		ChatCompletionChunk expectedChatCompletion = new ChatCompletionChunk("id", "chat.completion.chunk", 789L,
				"model", List.of(choice), null);

		given(this.mistralAiApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
			.willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(Flux.just(expectedChatCompletion));

		var result = this.chatModel.stream(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result.collectList().block().get(0).getResult().getOutput().getText()).isSameAs("Response");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	@Disabled("Currently stream() does not implement retry")
	public void mistralAiChatStreamNonTransientError() {
		given(this.mistralAiApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
			.willThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> this.chatModel.stream(new Prompt("text")));
	}

	@Test
	public void mistralAiEmbeddingTransientError() {

		EmbeddingList<Embedding> expectedEmbeddings = new EmbeddingList<>("list",
				List.of(new Embedding(0, new float[] { 9.9f, 8.8f })), "model", new MistralAiApi.Usage(10, 10, 10));

		given(this.mistralAiApi.embeddings(isA(EmbeddingRequest.class)))
			.willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(ResponseEntity.of(Optional.of(expectedEmbeddings)));

		var result = this.embeddingModel
			.call(new org.springframework.ai.embedding.EmbeddingRequest(List.of("text1", "text2"), null));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput()).isEqualTo(new float[] { 9.9f, 8.8f });
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void mistralAiEmbeddingNonTransientError() {
		given(this.mistralAiApi.embeddings(isA(EmbeddingRequest.class)))
			.willThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> this.embeddingModel
			.call(new org.springframework.ai.embedding.EmbeddingRequest(List.of("text1", "text2"), null)));
	}

	@Test
	public void mistralAiChatMixedTransientAndNonTransientErrors() {
		given(this.mistralAiApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
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
