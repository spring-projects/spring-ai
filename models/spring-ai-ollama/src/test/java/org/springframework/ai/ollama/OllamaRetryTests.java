/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.ollama;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the OllamaRetryTests class.
 *
 * @author Alexandros Pappas
 */
@ExtendWith(MockitoExtension.class)
class OllamaRetryTests {

	private static final String MODEL = OllamaModel.LLAMA3_2.getName();

	private TestRetryListener retryListener;

	private RetryTemplate retryTemplate;

	@Mock
	private OllamaApi ollamaApi;

	private OllamaChatModel chatModel;

	@BeforeEach
	public void beforeEach() {
		this.retryTemplate = RetryUtils.SHORT_RETRY_TEMPLATE;
		this.retryListener = new TestRetryListener();
		this.retryTemplate.setRetryListener(this.retryListener);

		this.chatModel = OllamaChatModel.builder()
			.ollamaApi(this.ollamaApi)
			.defaultOptions(OllamaChatOptions.builder().model(MODEL).temperature(0.9).build())
			.retryTemplate(this.retryTemplate)
			.build();
	}

	@Test
	void ollamaChatTransientError() {
		String promptText = "What is the capital of Bulgaria and what is the size? What it the national anthem?";
		var expectedChatResponse = new OllamaApi.ChatResponse("CHAT_COMPLETION_ID", Instant.now(),
				OllamaApi.Message.builder(OllamaApi.Message.Role.ASSISTANT).content("Response").build(), null, true,
				null, null, null, null, null, null);

		when(this.ollamaApi.chat(isA(OllamaApi.ChatRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(expectedChatResponse);

		var result = this.chatModel.call(new Prompt(promptText));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getText()).isSameAs("Response");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	void ollamaChatSuccessOnFirstAttempt() {
		String promptText = "Simple question";
		var expectedChatResponse = new OllamaApi.ChatResponse("CHAT_COMPLETION_ID", Instant.now(),
				OllamaApi.Message.builder(OllamaApi.Message.Role.ASSISTANT).content("Quick response").build(), null,
				true, null, null, null, null, null, null);

		when(this.ollamaApi.chat(isA(OllamaApi.ChatRequest.class))).thenReturn(expectedChatResponse);

		var result = this.chatModel.call(new Prompt(promptText));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getText()).isEqualTo("Quick response");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(0);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(0);
		verify(this.ollamaApi, times(1)).chat(isA(OllamaApi.ChatRequest.class));
	}

	@Test
	void ollamaChatNonTransientErrorShouldNotRetry() {
		String promptText = "Invalid request";

		when(this.ollamaApi.chat(isA(OllamaApi.ChatRequest.class)))
			.thenThrow(new NonTransientAiException("Model not found"));

		assertThatThrownBy(() -> this.chatModel.call(new Prompt(promptText)))
			.isInstanceOf(NonTransientAiException.class)
			.hasMessage("Model not found");

		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(0);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(0);
		verify(this.ollamaApi, times(1)).chat(isA(OllamaApi.ChatRequest.class));
	}

	@Test
	void ollamaChatWithMultipleMessages() {
		List<Message> messages = List.of(new UserMessage("What is AI?"), new UserMessage("Explain machine learning"));
		Prompt prompt = new Prompt(messages);

		var expectedChatResponse = new OllamaApi.ChatResponse("CHAT_COMPLETION_ID", Instant.now(),
				OllamaApi.Message.builder(OllamaApi.Message.Role.ASSISTANT)
					.content("AI is artificial intelligence...")
					.build(),
				null, true, null, null, null, null, null, null);

		when(this.ollamaApi.chat(isA(OllamaApi.ChatRequest.class)))
			.thenThrow(new TransientAiException("Temporary overload"))
			.thenReturn(expectedChatResponse);

		var result = this.chatModel.call(prompt);

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getText()).isEqualTo("AI is artificial intelligence...");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(1);
	}

	@Test
	void ollamaChatWithCustomOptions() {
		String promptText = "Custom temperature request";
		OllamaChatOptions customOptions = OllamaChatOptions.builder().model(MODEL).temperature(0.1).topP(0.9).build();

		var expectedChatResponse = new OllamaApi.ChatResponse("CHAT_COMPLETION_ID", Instant.now(),
				OllamaApi.Message.builder(OllamaApi.Message.Role.ASSISTANT).content("Deterministic response").build(),
				null, true, null, null, null, null, null, null);

		when(this.ollamaApi.chat(isA(OllamaApi.ChatRequest.class)))
			.thenThrow(new ResourceAccessException("Connection timeout"))
			.thenReturn(expectedChatResponse);

		var result = this.chatModel.call(new Prompt(promptText, customOptions));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getText()).isEqualTo("Deterministic response");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
	}

	@Test
	void ollamaChatWithEmptyResponse() {
		String promptText = "Edge case request";
		var expectedChatResponse = new OllamaApi.ChatResponse("CHAT_COMPLETION_ID", Instant.now(),
				OllamaApi.Message.builder(OllamaApi.Message.Role.ASSISTANT).content("").build(), null, true, null, null,
				null, null, null, null);

		when(this.ollamaApi.chat(isA(OllamaApi.ChatRequest.class)))
			.thenThrow(new TransientAiException("Rate limit exceeded"))
			.thenReturn(expectedChatResponse);

		var result = this.chatModel.call(new Prompt(promptText));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getText()).isEmpty();
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
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
