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

package org.springframework.ai.qianfan.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.image.ImageMessage;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.qianfan.QianFanChatModel;
import org.springframework.ai.qianfan.QianFanChatOptions;
import org.springframework.ai.qianfan.QianFanEmbeddingModel;
import org.springframework.ai.qianfan.QianFanEmbeddingOptions;
import org.springframework.ai.qianfan.QianFanImageModel;
import org.springframework.ai.qianfan.QianFanImageOptions;
import org.springframework.ai.qianfan.api.QianFanApi.ChatCompletion;
import org.springframework.ai.qianfan.api.QianFanApi.ChatCompletionChunk;
import org.springframework.ai.qianfan.api.QianFanApi.ChatCompletionRequest;
import org.springframework.ai.qianfan.api.QianFanApi.EmbeddingList;
import org.springframework.ai.qianfan.api.QianFanApi.EmbeddingRequest;
import org.springframework.ai.qianfan.api.QianFanApi.Usage;
import org.springframework.ai.qianfan.api.QianFanImageApi.Data;
import org.springframework.ai.qianfan.api.QianFanImageApi.QianFanImageRequest;
import org.springframework.ai.qianfan.api.QianFanImageApi.QianFanImageResponse;
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
 * @author Geng Rong
 */
@ExtendWith(MockitoExtension.class)
public class QianFanRetryTests {

	private TestRetryListener retryListener;

	private @Mock QianFanApi qianFanApi;

	private @Mock QianFanImageApi qianFanImageApi;

	private QianFanChatModel chatClient;

	private QianFanEmbeddingModel embeddingClient;

	private QianFanImageModel imageModel;

	@BeforeEach
	public void beforeEach() {
		RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;
		this.retryListener = new TestRetryListener();
		retryTemplate.registerListener(this.retryListener);

		this.chatClient = new QianFanChatModel(this.qianFanApi, QianFanChatOptions.builder().build(), retryTemplate);
		this.embeddingClient = new QianFanEmbeddingModel(this.qianFanApi, MetadataMode.EMBED,
				QianFanEmbeddingOptions.builder().build(), retryTemplate);
		this.imageModel = new QianFanImageModel(this.qianFanImageApi, QianFanImageOptions.builder().build(),
				retryTemplate);
	}

	@Test
	public void qianFanChatTransientError() {
		ChatCompletion expectedChatCompletion = new ChatCompletion("id", "chat.completion", 666L, "Response", "STOP",
				new Usage(10, 10, 10));

		when(this.qianFanApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(ResponseEntity.of(Optional.of(expectedChatCompletion)));

		var result = this.chatClient.call(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getContent()).isSameAs("Response");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void qianFanChatNonTransientError() {
		when(this.qianFanApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
			.thenThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> this.chatClient.call(new Prompt("text")));
	}

	@Test
	@Disabled("Currently stream() does not implmement retry")
	public void qianFanChatStreamTransientError() {
		ChatCompletionChunk expectedChatCompletion = new ChatCompletionChunk("id", "chat.completion", 666L, "Response",
				"", true, null);

		when(this.qianFanApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(Flux.just(expectedChatCompletion));

		var result = this.chatClient.stream(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(Objects.requireNonNull(result.collectList().block()).get(0).getResult().getOutput().getContent())
			.isSameAs("Response");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void qianFanChatStreamNonTransientError() {
		when(this.qianFanApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
			.thenThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> this.chatClient.stream(new Prompt("text")).collectList().block());
	}

	@Test
	public void qianFanEmbeddingTransientError() {
		QianFanApi.Embedding embedding = new QianFanApi.Embedding(1, new float[] { 9.9f, 8.8f });
		EmbeddingList expectedEmbeddings = new EmbeddingList("embedding_list", List.of(embedding), "model", null, null,
				new Usage(10, 10, 10));

		when(this.qianFanApi.embeddings(isA(EmbeddingRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(ResponseEntity.of(Optional.of(expectedEmbeddings)));

		var result = this.embeddingClient
			.call(new org.springframework.ai.embedding.EmbeddingRequest(List.of("text1", "text2"), null));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput()).isEqualTo(new float[] { 9.9f, 8.8f });
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void qianFanEmbeddingNonTransientError() {
		when(this.qianFanApi.embeddings(isA(EmbeddingRequest.class)))
			.thenThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> this.embeddingClient
			.call(new org.springframework.ai.embedding.EmbeddingRequest(List.of("text1", "text2"), null)));
	}

	@Test
	public void qianFanImageTransientError() {

		var expectedResponse = new QianFanImageResponse("1", 678L, List.of(new Data(1, "b64")));

		when(this.qianFanImageApi.createImage(isA(QianFanImageRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(ResponseEntity.of(Optional.of(expectedResponse)));

		var result = this.imageModel.call(new ImagePrompt(List.of(new ImageMessage("Image Message"))));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getB64Json()).isEqualTo("b64");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void qianFanImageNonTransientError() {
		when(this.qianFanImageApi.createImage(isA(QianFanImageRequest.class)))
			.thenThrow(new RuntimeException("Transient Error 1"));
		assertThrows(RuntimeException.class,
				() -> this.imageModel.call(new ImagePrompt(List.of(new ImageMessage("Image Message")))));
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
