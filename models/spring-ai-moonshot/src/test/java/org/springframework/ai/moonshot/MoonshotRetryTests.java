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
package org.springframework.ai.moonshot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.moonshot.api.MoonshotApi;
import org.springframework.ai.moonshot.api.MoonshotApi.ChatCompletion;
import org.springframework.ai.moonshot.api.MoonshotApi.ChatCompletionChunk;
import org.springframework.ai.moonshot.api.MoonshotApi.ChatCompletionFinishReason;
import org.springframework.ai.moonshot.api.MoonshotApi.ChatCompletionMessage;
import org.springframework.ai.moonshot.api.MoonshotApi.ChatCompletionMessage.Role;
import org.springframework.ai.moonshot.api.MoonshotApi.ChatCompletionRequest;
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
import static org.mockito.Mockito.when;

/**
 * @author Geng Rong
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
public class MoonshotRetryTests {

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

	private @Mock MoonshotApi moonshotApi;

	private MoonshotChatModel chatModel;

	@BeforeEach
	public void beforeEach() {
		RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;
		retryListener = new TestRetryListener();
		retryTemplate.registerListener(retryListener);

		chatModel = new MoonshotChatModel(moonshotApi,
				MoonshotChatOptions.builder()
					.withTemperature(0.7)
					.withTopP(1.0)
					.withModel(MoonshotApi.ChatModel.MOONSHOT_V1_32K.getValue())
					.build(),
				null, retryTemplate);
	}

	@Test
	public void moonshotChatTransientError() {

		var choice = new ChatCompletion.Choice(0, new ChatCompletionMessage("Response", Role.ASSISTANT),
				ChatCompletionFinishReason.STOP);
		ChatCompletion expectedChatCompletion = new ChatCompletion("id", "chat.completion", 789l, "model",
				List.of(choice), new MoonshotApi.Usage(10, 10, 10));

		when(moonshotApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
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
	public void moonshotChatNonTransientError() {
		when(moonshotApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
			.thenThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> chatModel.call(new Prompt("text")));
	}

	@Test
	public void moonshotChatStreamTransientError() {

		var choice = new ChatCompletionChunk.ChunkChoice(0, new ChatCompletionMessage("Response", Role.ASSISTANT),
				ChatCompletionFinishReason.STOP, null);
		ChatCompletionChunk expectedChatCompletion = new ChatCompletionChunk("id", "chat.completion.chunk", 789l,
				"model", List.of(choice));

		when(moonshotApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
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
	public void moonshotChatStreamNonTransientError() {
		when(moonshotApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
			.thenThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> chatModel.stream(new Prompt("text")).collectList().block());
	}

}
