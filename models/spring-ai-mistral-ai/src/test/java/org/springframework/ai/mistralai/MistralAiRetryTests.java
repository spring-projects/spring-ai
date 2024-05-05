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
package org.springframework.ai.mistralai;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.MetadataMode;
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
import static org.mockito.Mockito.when;

/**
 * @author Christian Tzolov
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
public class MistralAiRetryTests {

	private class TestRetryListener implements RetryListener {

		int onErrorRetryCount = 0;

		int onSuccessRetryCount = 0;

		@Override
		public <T, E extends Throwable> void onSuccess(RetryContext context, RetryCallback<T, E> callback, T result) {
			onSuccessRetryCount = context.getRetryCount();
		}

		@Override
		public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
				Throwable throwable) {
			onErrorRetryCount = context.getRetryCount();
		}

	}

	private TestRetryListener retryListener;

	private RetryTemplate retryTemplate;

	private @Mock MistralAiApi mistralAiApi;

	private MistralAiChatClient chatClient;

	private MistralAiEmbeddingClient embeddingClient;

	@BeforeEach
	public void beforeEach() {
		retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;
		retryListener = new TestRetryListener();
		retryTemplate.registerListener(retryListener);

		chatClient = new MistralAiChatClient(mistralAiApi,
				MistralAiChatOptions.builder()
					.withTemperature(0.7f)
					.withTopP(1f)
					.withSafePrompt(false)
					.withModel(MistralAiApi.ChatModel.TINY.getValue())
					.build(),
				null, retryTemplate);
		embeddingClient = new MistralAiEmbeddingClient(mistralAiApi, MetadataMode.EMBED,
				MistralAiEmbeddingOptions.builder().withModel(MistralAiApi.EmbeddingModel.EMBED.getValue()).build(),
				retryTemplate);
	}

	@Test
	public void mistralAiChatTransientError() {

		var choice = new ChatCompletion.Choice(0, new ChatCompletionMessage("Response", Role.ASSISTANT),
				ChatCompletionFinishReason.STOP, null);
		ChatCompletion expectedChatCompletion = new ChatCompletion("id", "chat.completion", 789l, "model",
				List.of(choice), new MistralAiApi.Usage(10, 10, 10));

		when(mistralAiApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(ResponseEntity.of(Optional.of(expectedChatCompletion)));

		var result = chatClient.call(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getContent()).isSameAs("Response");
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void mistralAiChatNonTransientError() {
		when(mistralAiApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
				.thenThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> chatClient.call(new Prompt("text")));
	}

	@Test
	public void mistralAiChatStreamTransientError() {

		var choice = new ChatCompletionChunk.ChunkChoice(0, new ChatCompletionMessage("Response", Role.ASSISTANT),
				ChatCompletionFinishReason.STOP, null);
		ChatCompletionChunk expectedChatCompletion = new ChatCompletionChunk("id", "chat.completion.chunk", 789l,
				"model", List.of(choice));

		when(mistralAiApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(Flux.just(expectedChatCompletion));

		var result = chatClient.stream(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result.collectList().block().get(0).getResult().getOutput().getContent()).isSameAs("Response");
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void mistralAiChatStreamNonTransientError() {
		when(mistralAiApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
				.thenThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> chatClient.stream(new Prompt("text")));
	}

	@Test
	public void mistralAiEmbeddingTransientError() {

		EmbeddingList<Embedding> expectedEmbeddings = new EmbeddingList<>("list",
				List.of(new Embedding(0, List.of(9.9, 8.8))), "model", new MistralAiApi.Usage(10, 10, 10));

		when(mistralAiApi.embeddings(isA(EmbeddingRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(ResponseEntity.of(Optional.of(expectedEmbeddings)));

		var result = embeddingClient
			.call(new org.springframework.ai.embedding.EmbeddingRequest(List.of("text1", "text2"), null));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput()).isEqualTo(List.of(9.9, 8.8));
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void mistralAiEmbeddingNonTransientError() {
		when(mistralAiApi.embeddings(isA(EmbeddingRequest.class)))
				.thenThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> embeddingClient
				.call(new org.springframework.ai.embedding.EmbeddingRequest(List.of("text1", "text2"), null)));
	}

}
