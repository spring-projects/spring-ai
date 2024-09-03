/*
 * Copyright 2024 - 2024 the original author or authors.
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
package org.springframework.ai.zhipuai.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.image.ImageMessage;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.zhipuai.ZhiPuAiChatModel;
import org.springframework.ai.zhipuai.ZhiPuAiChatOptions;
import org.springframework.ai.zhipuai.ZhiPuAiEmbeddingModel;
import org.springframework.ai.zhipuai.ZhiPuAiEmbeddingOptions;
import org.springframework.ai.zhipuai.ZhiPuAiImageModel;
import org.springframework.ai.zhipuai.ZhiPuAiImageOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletion;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletionChunk;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletionFinishReason;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletionMessage;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletionMessage.Role;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.ChatCompletionRequest;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.Embedding;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.EmbeddingList;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi.EmbeddingRequest;
import org.springframework.ai.zhipuai.api.ZhiPuAiImageApi.Data;
import org.springframework.ai.zhipuai.api.ZhiPuAiImageApi.ZhiPuAiImageRequest;
import org.springframework.ai.zhipuai.api.ZhiPuAiImageApi.ZhiPuAiImageResponse;
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
import static org.mockito.Mockito.when;

/**
 * @author Geng Rong
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
public class ZhiPuAiRetryTests {

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

	private @Mock ZhiPuAiApi zhiPuAiApi;

	private @Mock ZhiPuAiImageApi zhiPuAiImageApi;

	private ZhiPuAiChatModel chatModel;

	private ZhiPuAiEmbeddingModel embeddingModel;

	private ZhiPuAiImageModel imageModel;

	@BeforeEach
	public void beforeEach() {
		retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;
		retryListener = new TestRetryListener();
		retryTemplate.registerListener(retryListener);

		chatModel = new ZhiPuAiChatModel(zhiPuAiApi, ZhiPuAiChatOptions.builder().build(), null, retryTemplate);
		embeddingModel = new ZhiPuAiEmbeddingModel(zhiPuAiApi, MetadataMode.EMBED,
				ZhiPuAiEmbeddingOptions.builder().build(), retryTemplate);
		imageModel = new ZhiPuAiImageModel(zhiPuAiImageApi, ZhiPuAiImageOptions.builder().build(), retryTemplate);
	}

	@Test
	public void zhiPuAiChatTransientError() {

		var choice = new ChatCompletion.Choice(ChatCompletionFinishReason.STOP, 0,
				new ChatCompletionMessage("Response", Role.ASSISTANT), null);
		ChatCompletion expectedChatCompletion = new ChatCompletion("id", List.of(choice), 666l, "model", null, null,
				new ZhiPuAiApi.Usage(10, 10, 10));

		when(zhiPuAiApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(ResponseEntity.of(Optional.of(expectedChatCompletion)));

		var result = chatModel.call(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getContent()).isSameAs("Response");
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void zhiPuAiChatNonTransientError() {
		when(zhiPuAiApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
				.thenThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> chatModel.call(new Prompt("text")));
	}

	@Test
	public void zhiPuAiChatStreamTransientError() {

		var choice = new ChatCompletionChunk.ChunkChoice(ChatCompletionFinishReason.STOP, 0,
				new ChatCompletionMessage("Response", Role.ASSISTANT), null);
		ChatCompletionChunk expectedChatCompletion = new ChatCompletionChunk("id", List.of(choice), 666l, "model", null,
				null);

		when(zhiPuAiApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(Flux.just(expectedChatCompletion));

		var result = chatModel.stream(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result.collectList().block().get(0).getResult().getOutput().getContent()).isSameAs("Response");
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void zhiPuAiChatStreamNonTransientError() {
		when(zhiPuAiApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
				.thenThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> chatModel.stream(new Prompt("text")));
	}

	@Test
	public void zhiPuAiEmbeddingTransientError() {

		EmbeddingList<Embedding> expectedEmbeddings = new EmbeddingList<>("list",
				List.of(new Embedding(0, new float[] { 9.9f, 8.8f })), "model", new ZhiPuAiApi.Usage(10, 10, 10));

		when(zhiPuAiApi.embeddings(isA(EmbeddingRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(ResponseEntity.of(Optional.of(expectedEmbeddings)));

		var result = embeddingModel
			.call(new org.springframework.ai.embedding.EmbeddingRequest(List.of("text1", "text2"), null));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput()).isEqualTo(new float[] { 9.9f, 8.8f });
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void zhiPuAiEmbeddingNonTransientError() {
		when(zhiPuAiApi.embeddings(isA(EmbeddingRequest.class)))
				.thenThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> embeddingModel
				.call(new org.springframework.ai.embedding.EmbeddingRequest(List.of("text1", "text2"), null)));
	}

	@Test
	public void zhiPuAiImageTransientError() {

		var expectedResponse = new ZhiPuAiImageResponse(678l, List.of(new Data("url678")));

		when(zhiPuAiImageApi.createImage(isA(ZhiPuAiImageRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(ResponseEntity.of(Optional.of(expectedResponse)));

		var result = imageModel.call(new ImagePrompt(List.of(new ImageMessage("Image Message"))));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getUrl()).isEqualTo("url678");
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void zhiPuAiImageNonTransientError() {
		when(zhiPuAiImageApi.createImage(isA(ZhiPuAiImageRequest.class)))
				.thenThrow(new RuntimeException("Transient Error 1"));
		assertThrows(RuntimeException.class,
				() -> imageModel.call(new ImagePrompt(List.of(new ImageMessage("Image Message")))));
	}

}
