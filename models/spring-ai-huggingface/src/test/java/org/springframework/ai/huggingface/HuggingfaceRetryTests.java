/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.huggingface;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.huggingface.api.HuggingfaceApi;
import org.springframework.ai.model.tool.ToolCallingManager;
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
 * Unit tests for retry logic in {@link HuggingfaceChatModel}.
 *
 * @author Myeongdeok Kang
 */
@ExtendWith(MockitoExtension.class)
class HuggingfaceRetryTests {

	private static final String MODEL = "meta-llama/Llama-3.2-3B-Instruct";

	private TestRetryListener retryListener;

	private RetryTemplate retryTemplate;

	@Mock
	private HuggingfaceApi huggingfaceApi;

	private HuggingfaceChatModel chatModel;

	@BeforeEach
	public void beforeEach() {
		this.retryTemplate = RetryUtils.SHORT_RETRY_TEMPLATE;
		this.retryListener = new TestRetryListener();
		this.retryTemplate.setRetryListener(this.retryListener);

		this.chatModel = HuggingfaceChatModel.builder()
			.huggingfaceApi(this.huggingfaceApi)
			.defaultOptions(HuggingfaceChatOptions.builder().model(MODEL).temperature(0.9).build())
			.toolCallingManager(ToolCallingManager.builder().build())
			.retryTemplate(this.retryTemplate)
			.build();
	}

	@Test
	void huggingfaceChatTransientError() {
		String promptText = "What is the capital of Bulgaria and what is the size? What it the national anthem?";
		var expectedChatResponse = new HuggingfaceApi.ChatResponse("id-123", "chat.completion",
				System.currentTimeMillis(), MODEL,
				List.of(new HuggingfaceApi.Choice(0, new HuggingfaceApi.Message("assistant", "Response"), "stop")),
				new HuggingfaceApi.Usage(10, 20, 30), null);

		when(this.huggingfaceApi.chat(isA(HuggingfaceApi.ChatRequest.class)))
			.thenThrow(new TransientAiException("Transient Error 1"))
			.thenThrow(new TransientAiException("Transient Error 2"))
			.thenReturn(expectedChatResponse);

		var result = this.chatModel.call(new Prompt(promptText));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getText()).isSameAs("Response");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
		assertThat(this.retryListener.retryCount).isEqualTo(2);
	}

	@Test
	void huggingfaceChatSuccessOnFirstAttempt() {
		String promptText = "Simple question";
		var expectedChatResponse = new HuggingfaceApi.ChatResponse("id-123", "chat.completion",
				System.currentTimeMillis(), MODEL, List.of(new HuggingfaceApi.Choice(0,
						new HuggingfaceApi.Message("assistant", "Quick response"), "stop")),
				new HuggingfaceApi.Usage(5, 10, 15), null);

		when(this.huggingfaceApi.chat(isA(HuggingfaceApi.ChatRequest.class))).thenReturn(expectedChatResponse);

		var result = this.chatModel.call(new Prompt(promptText));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getText()).isEqualTo("Quick response");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(0);
		assertThat(this.retryListener.retryCount).isEqualTo(0);
		verify(this.huggingfaceApi, times(1)).chat(isA(HuggingfaceApi.ChatRequest.class));
	}

	@Test
	void huggingfaceChatNonTransientErrorShouldNotRetry() {
		String promptText = "Invalid request";

		when(this.huggingfaceApi.chat(isA(HuggingfaceApi.ChatRequest.class)))
			.thenThrow(new NonTransientAiException("Model not found"));

		assertThatThrownBy(() -> this.chatModel.call(new Prompt(promptText)))
			.isInstanceOf(NonTransientAiException.class)
			.hasMessage("Model not found");

		verify(this.huggingfaceApi, times(1)).chat(isA(HuggingfaceApi.ChatRequest.class));
	}

	@Test
	void huggingfaceChatWithMultipleMessages() {
		List<Message> messages = List.of(new UserMessage("What is AI?"), new UserMessage("Explain machine learning"));
		Prompt prompt = new Prompt(messages);

		var expectedChatResponse = new HuggingfaceApi.ChatResponse("id-123", "chat.completion",
				System.currentTimeMillis(), MODEL,
				List.of(new HuggingfaceApi.Choice(0,
						new HuggingfaceApi.Message("assistant", "AI is artificial intelligence..."), "stop")),
				new HuggingfaceApi.Usage(15, 30, 45), null);

		when(this.huggingfaceApi.chat(isA(HuggingfaceApi.ChatRequest.class)))
			.thenThrow(new TransientAiException("Temporary overload"))
			.thenReturn(expectedChatResponse);

		var result = this.chatModel.call(prompt);

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getText()).isEqualTo("AI is artificial intelligence...");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
		assertThat(this.retryListener.retryCount).isEqualTo(1);
	}

	@Test
	void huggingfaceChatWithCustomOptions() {
		String promptText = "Custom temperature request";
		HuggingfaceChatOptions customOptions = HuggingfaceChatOptions.builder()
			.model(MODEL)
			.temperature(0.1)
			.topP(0.9)
			.build();

		var expectedChatResponse = new HuggingfaceApi.ChatResponse("id-123", "chat.completion",
				System.currentTimeMillis(), MODEL, List.of(new HuggingfaceApi.Choice(0,
						new HuggingfaceApi.Message("assistant", "Deterministic response"), "stop")),
				new HuggingfaceApi.Usage(8, 12, 20), null);

		when(this.huggingfaceApi.chat(isA(HuggingfaceApi.ChatRequest.class)))
			.thenThrow(new ResourceAccessException("Connection timeout"))
			.thenReturn(expectedChatResponse);

		var result = this.chatModel.call(new Prompt(promptText, customOptions));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getText()).isEqualTo("Deterministic response");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
	}

	@Test
	void huggingfaceChatWithEmptyResponse() {
		String promptText = "Edge case request";
		var expectedChatResponse = new HuggingfaceApi.ChatResponse("id-123", "chat.completion",
				System.currentTimeMillis(), MODEL,
				List.of(new HuggingfaceApi.Choice(0, new HuggingfaceApi.Message("assistant", ""), "stop")),
				new HuggingfaceApi.Usage(5, 0, 5), null);

		when(this.huggingfaceApi.chat(isA(HuggingfaceApi.ChatRequest.class)))
			.thenThrow(new TransientAiException("Rate limit exceeded"))
			.thenReturn(expectedChatResponse);

		var result = this.chatModel.call(new Prompt(promptText));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getText()).isEmpty();
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
	}

	private static class TestRetryListener implements RetryListener {

		int retryCount = 0;

		int onSuccessRetryCount = 0;

		@Override
		public void onRetrySuccess(final RetryPolicy retryPolicy, final Retryable<?> retryable, final Object result) {
			this.onSuccessRetryCount++;
		}

		@Override
		public void beforeRetry(RetryPolicy retryPolicy, Retryable<?> retryable) {
			this.retryCount++;
		}

	}

}
