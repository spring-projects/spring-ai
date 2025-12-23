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

package org.springframework.ai.zhipuai.api;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingOptions;
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
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;

/**
 * @author Geng Rong
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
public class ZhiPuAiRetryTests {

	private TestRetryListener retryListener;

	private RetryTemplate retryTemplate;

	private @Mock ZhiPuAiApi zhiPuAiApi;

	private @Mock ZhiPuAiImageApi zhiPuAiImageApi;

	private ZhiPuAiChatModel chatModel;

	private ZhiPuAiEmbeddingModel embeddingModel;

	private ZhiPuAiImageModel imageModel;

	@BeforeEach
	public void beforeEach() {
		this.retryTemplate = RetryUtils.SHORT_RETRY_TEMPLATE;
		this.retryListener = new TestRetryListener();
		this.retryTemplate.setRetryListener(this.retryListener);

		this.chatModel = new ZhiPuAiChatModel(this.zhiPuAiApi, ZhiPuAiChatOptions.builder().build(),
				this.retryTemplate);
		this.embeddingModel = new ZhiPuAiEmbeddingModel(this.zhiPuAiApi, MetadataMode.EMBED,
				ZhiPuAiEmbeddingOptions.builder().build(), this.retryTemplate);
		this.imageModel = new ZhiPuAiImageModel(this.zhiPuAiImageApi, ZhiPuAiImageOptions.builder().build(),
				this.retryTemplate);
	}

	@Test
	public void zhiPuAiChatTransientError() {

		var choice = new ChatCompletion.Choice(ChatCompletionFinishReason.STOP, 0,
				new ChatCompletionMessage("Response", Role.ASSISTANT), null);
		ChatCompletion expectedChatCompletion = new ChatCompletion("id", List.of(choice), 666L, "model", null, null,
				new ZhiPuAiApi.Usage(10, 10, 10));

		given(this.zhiPuAiApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
			.willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(ResponseEntity.of(Optional.of(expectedChatCompletion)));

		var result = this.chatModel.call(new Prompt("text", ChatOptions.builder().build()));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getText()).isSameAs("Response");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void zhiPuAiChatNonTransientError() {
		given(this.zhiPuAiApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
			.willThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class,
				() -> this.chatModel.call(new Prompt("text", ChatOptions.builder().build())));
	}

	@Test
	public void zhiPuAiChatStreamTransientError() {

		var choice = new ChatCompletionChunk.ChunkChoice(ChatCompletionFinishReason.STOP, 0,
				new ChatCompletionMessage("Response", Role.ASSISTANT), null);
		ChatCompletionChunk expectedChatCompletion = new ChatCompletionChunk("id", List.of(choice), 666L, "model", null,
				null, null);

		given(this.zhiPuAiApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
			.willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(Flux.just(expectedChatCompletion));

		var result = this.chatModel.stream(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result.collectList().block().get(0).getResult().getOutput().getText()).isSameAs("Response");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void zhiPuAiChatStreamNonTransientError() {
		given(this.zhiPuAiApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
			.willThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> this.chatModel.stream(new Prompt("text")).collectList().block());
	}

	@Test
	public void zhiPuAiEmbeddingTransientError() {

		EmbeddingList<Embedding> expectedEmbeddings = new EmbeddingList<>("list",
				List.of(new Embedding(0, new float[] { 9.9f, 8.8f }), new Embedding(0, new float[] { 9.9f, 8.8f })),
				"model", new ZhiPuAiApi.Usage(10, 10, 10));

		given(this.zhiPuAiApi.embeddings(isA(EmbeddingRequest.class)))
			.willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(ResponseEntity.of(Optional.of(expectedEmbeddings)));
		EmbeddingOptions options = ZhiPuAiEmbeddingOptions.builder().model("model").build();
		var result = this.embeddingModel
			.call(new org.springframework.ai.embedding.EmbeddingRequest(List.of("text1", "text2"), options));

		assertThat(result.getResults().size()).isEqualTo(2);
		assertThat(result).isNotNull();
		// choose the first result
		assertThat(result.getResult().getOutput()).isEqualTo(new float[] { 9.9f, 8.8f });
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void zhiPuAiEmbeddingNonTransientError() {
		given(this.zhiPuAiApi.embeddings(isA(EmbeddingRequest.class)))
			.willThrow(new RuntimeException("Non Transient Error"));
		EmbeddingOptions options = ZhiPuAiEmbeddingOptions.builder().model("model").build();
		assertThrows(RuntimeException.class, () -> this.embeddingModel
			.call(new org.springframework.ai.embedding.EmbeddingRequest(List.of("text1", "text2"), options)));
	}

	@Test
	public void zhiPuAiImageTransientError() {

		var expectedResponse = new ZhiPuAiImageResponse(678L, List.of(new Data("url678", List.of())));

		given(this.zhiPuAiImageApi.createImage(isA(ZhiPuAiImageRequest.class)))
			.willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(ResponseEntity.of(Optional.of(expectedResponse)));

		var result = this.imageModel.call(new ImagePrompt(List.of(new ImageMessage("Image Message"))));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getUrl()).isEqualTo("url678");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void zhiPuAiImageNonTransientError() {
		given(this.zhiPuAiImageApi.createImage(isA(ZhiPuAiImageRequest.class)))
			.willThrow(new RuntimeException("Transient Error 1"));
		assertThrows(RuntimeException.class,
				() -> this.imageModel.call(new ImagePrompt(List.of(new ImageMessage("Image Message")))));
	}

	private static class TestRetryListener implements RetryListener {

		int onErrorRetryCount = 0;

		int onSuccessRetryCount = 0;

		@Override
		public void beforeRetry(final RetryPolicy retryPolicy, final Retryable<?> retryable) {
			// Count each retry attempt
			this.onErrorRetryCount++;
		}

		@Override
		public void onRetrySuccess(final RetryPolicy retryPolicy, final Retryable<?> retryable, final Object result) {
			// Count successful retries - we increment when we succeed after a failure
			this.onSuccessRetryCount++;
		}

	}

}
