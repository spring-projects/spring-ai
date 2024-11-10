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

package api;

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
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.solar.SolarChatModel;
import org.springframework.ai.solar.SolarChatOptions;
import org.springframework.ai.solar.SolarEmbeddingModel;
import org.springframework.ai.solar.SolarEmbeddingOptions;
import org.springframework.ai.solar.api.SolarApi;
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
 * @author Seunghyeon Ji
 */
@ExtendWith(MockitoExtension.class)
public class SolarRetryTests {

	private TestRetryListener retryListener;

	private @Mock SolarApi solarApi;

	private SolarChatModel chatClient;

	private SolarEmbeddingModel embeddingClient;

	@BeforeEach
	public void beforeEach() {
		RetryTemplate retryTemplate = RetryUtils.SHORT_RETRY_TEMPLATE;
		this.retryListener = new TestRetryListener();
		retryTemplate.registerListener(this.retryListener);

		this.chatClient = new SolarChatModel(this.solarApi, SolarChatOptions.builder().build(), retryTemplate);
		this.embeddingClient = new SolarEmbeddingModel(this.solarApi, MetadataMode.EMBED,
				SolarEmbeddingOptions.builder().build(), retryTemplate);
	}

	@Test
	public void solarChatTransientError() {
		List<SolarApi.ChatCompletion.Choice> choices = List.of(
				new SolarApi.ChatCompletion.Choice(
						0,
						new SolarApi.ChatCompletion.Message("assistant", "Response"),
						null,
						"STOP"
				)
		);

		SolarApi.ChatCompletion expectedChatCompletion = new SolarApi.ChatCompletion(
				"id",
				"chat.completion",
				666L,
				SolarApi.DEFAULT_CHAT_MODEL,
				choices,
				new SolarApi.Usage(10, 10, 10),
				null
		);

		given(this.solarApi.chatCompletionEntity(isA(SolarApi.ChatCompletionRequest.class)))
				.willThrow(new TransientAiException("Transient Error 1"))
				.willThrow(new TransientAiException("Transient Error 2"))
				.willReturn(ResponseEntity.of(Optional.of(expectedChatCompletion)));

		var result = this.chatClient.call(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getContent()).isEqualTo("Response");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void solarChatNonTransientError() {
		given(this.solarApi.chatCompletionEntity(isA(SolarApi.ChatCompletionRequest.class)))
			.willThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> this.chatClient.call(new Prompt("text")));
	}

	@Test
	public void solarEmbeddingTransientError() {
		SolarApi.Embedding embedding = new SolarApi.Embedding(1, new float[] { 9.9f, 8.8f });
		SolarApi.EmbeddingList expectedEmbeddings = new SolarApi.EmbeddingList("embedding_list", List.of(embedding), "model", null, null,
				new SolarApi.Usage(10, 10, 10));

		given(this.solarApi.embeddings(isA(SolarApi.EmbeddingRequest.class)))
			.willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(ResponseEntity.of(Optional.of(expectedEmbeddings)));

		var result = this.embeddingClient
			.call(new org.springframework.ai.embedding.EmbeddingRequest(List.of("text1", "text2"), null));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput()).isEqualTo(new float[] { 9.9f, 8.8f });
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void solarEmbeddingNonTransientError() {
		given(this.solarApi.embeddings(isA(SolarApi.EmbeddingRequest.class)))
			.willThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> this.embeddingClient
			.call(new org.springframework.ai.embedding.EmbeddingRequest(List.of("text1", "text2"), null)));
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
