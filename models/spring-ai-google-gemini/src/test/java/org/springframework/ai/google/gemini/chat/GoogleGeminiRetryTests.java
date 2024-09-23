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
package org.springframework.ai.google.gemini.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.google.gemini.GoogleGeminiChatModel;
import org.springframework.ai.google.gemini.GoogleGeminiChatOptions;
import org.springframework.ai.google.gemini.api.GoogleGeminiApi;
import org.springframework.ai.google.gemini.api.GoogleGeminiApi.*;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;

/**
 * @author Geng Rong
 */
@ExtendWith(MockitoExtension.class)
public class GoogleGeminiRetryTests {

	private static class TestRetryListener implements RetryListener {

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

	private @Mock GoogleGeminiApi googleGeminiApi;

	private GoogleGeminiChatModel chatModel;

	@BeforeEach
	public void beforeEach() {
		RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;
		retryListener = new TestRetryListener();
		retryTemplate.registerListener(retryListener);

		chatModel = new GoogleGeminiChatModel(googleGeminiApi, GoogleGeminiChatOptions.builder().build(),
				retryTemplate);
	}

	@Test
	public void googleGeminiChatTransientError() {

		var choice = new ChatCompletionMessage(ChatCompletionMessage.Role.ASSISTANT, "Response");
		ChatCompletion expectedChatCompletion = new ChatCompletion(List.of(new Candidate(choice)), null,
				new GoogleGeminiApi.Usage(10, 10, 10, 10, 10, 10));

		when(googleGeminiApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(ResponseEntity.of(Optional.of(expectedChatCompletion)));

		var result = chatModel.call(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getText()).isSameAs("Response");
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void googleGeminiChatNonTransientError() {
		when(googleGeminiApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
			.thenThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> chatModel.call(new Prompt("text")));
	}

	@Test
	public void googleGeminiChatStreamTransientError() {

		var choice = new ChatCompletionMessage(ChatCompletionMessage.Role.ASSISTANT, "Response");
		var completion = new ChatCompletion(List.of(new Candidate(choice)), null,
				new GoogleGeminiApi.Usage(10, 10, 10, 10, 10, 10));

		when(googleGeminiApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(Flux.just(completion));

		var result = chatModel.stream(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(Objects.requireNonNull(result.collectList().block()).get(0).getResult().getOutput().getText())
			.isSameAs("Response");
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void googleGeminiChatStreamNonTransientError() {
		when(googleGeminiApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
			.thenThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> chatModel.stream(new Prompt("text")));
	}

}
