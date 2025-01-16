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

package org.springframework.ai.hunyuan;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.hunyuan.api.HunYuanApi;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.hunyuan.api.HunYuanApi.ChatCompletion;
import org.springframework.ai.hunyuan.api.HunYuanApi.ChatCompletionChunk;
import org.springframework.ai.hunyuan.api.HunYuanApi.ChatCompletionFinishReason;
import org.springframework.ai.hunyuan.api.HunYuanApi.ChatCompletionMessage;
import org.springframework.ai.hunyuan.api.HunYuanApi.ChatCompletionMessage.Role;
import org.springframework.ai.hunyuan.api.HunYuanApi.ChatCompletionRequest;
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
 * @author Geng Rong
 * @author Alexandros Pappas
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
public class HunYuanRetryTests {

	private TestRetryListener retryListener;

	private @Mock HunYuanApi moonshotApi;

	private HunYuanChatModel chatModel;

	@BeforeEach
	public void beforeEach() {
		RetryTemplate retryTemplate = RetryUtils.SHORT_RETRY_TEMPLATE;
		this.retryListener = new TestRetryListener();
		retryTemplate.registerListener(this.retryListener);

		this.chatModel = new HunYuanChatModel(this.moonshotApi,
				HunYuanChatOptions.builder()
					.temperature(0.7)
					.topP(1.0)
					.model(HunYuanApi.ChatModel.HUNYUAN_PRO.getValue())
					.build(),
				null, retryTemplate);
	}

	@Test
	public void moonshotChatTransientError() {

		var choice = new ChatCompletion.Choice(0, new ChatCompletionMessage("Response", Role.assistant),
				ChatCompletionFinishReason.STOP,null);
		ChatCompletion expectedChatCompletion = new ChatCompletion("id", null, 789L, "model",
				List.of(choice), new HunYuanApi.Usage(10, 10, 10),null,null,null,null,null);
		HunYuanApi.ChatCompletionResponse chatCompletionResponse = new HunYuanApi.ChatCompletionResponse(expectedChatCompletion);

		given(this.moonshotApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
			.willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(ResponseEntity.of(Optional.of(chatCompletionResponse)));

		var result = this.chatModel.call(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getText()).isSameAs("Response");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void moonshotChatNonTransientError() {
		given(this.moonshotApi.chatCompletionEntity(isA(ChatCompletionRequest.class)))
			.willThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> this.chatModel.call(new Prompt("text")));
	}

	@Test
	public void moonshotChatStreamTransientError() {

		var choice = new ChatCompletionChunk.ChunkChoice(0, new ChatCompletionMessage("Response", Role.assistant),
				ChatCompletionFinishReason.STOP, null);
		ChatCompletionChunk expectedChatCompletion = new ChatCompletionChunk("id", "chat.completion.chunk", 789L,
				"model", List.of(choice));

		given(this.moonshotApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
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
	public void moonshotChatStreamNonTransientError() {
		given(this.moonshotApi.chatCompletionStream(isA(ChatCompletionRequest.class)))
			.willThrow(new RuntimeException("Non Transient Error"));
		assertThrows(RuntimeException.class, () -> this.chatModel.stream(new Prompt("text")).collectList().block());
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
