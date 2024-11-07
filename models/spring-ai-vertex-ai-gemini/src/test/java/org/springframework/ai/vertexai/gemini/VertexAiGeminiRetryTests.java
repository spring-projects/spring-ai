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

package org.springframework.ai.vertexai.gemini;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Candidate;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.any;

/**
 * @author Mark Pollack
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
public class VertexAiGeminiRetryTests {

	private TestRetryListener retryListener;

	private RetryTemplate retryTemplate;

	@Mock
	private VertexAI vertexAI;

	@Mock
	private GenerativeModel mockGenerativeModel;

	private TestVertexAiGeminiChatModel chatModel;

	@BeforeEach
	public void setUp() {
		this.retryTemplate = RetryUtils.SHORT_RETRY_TEMPLATE;
		this.retryListener = new TestRetryListener();
		this.retryTemplate.registerListener(this.retryListener);

		this.chatModel = new TestVertexAiGeminiChatModel(this.vertexAI,
				VertexAiGeminiChatOptions.builder()
					.withTemperature(0.7)
					.withTopP(1.0)
					.withModel(VertexAiGeminiChatModel.ChatModel.GEMINI_PRO.getValue())
					.build(),
				null, Collections.emptyList(), this.retryTemplate);

		this.chatModel.setMockGenerativeModel(this.mockGenerativeModel);
	}

	@Test
	public void vertexAiGeminiChatTransientError() throws IOException {
		// Create a mocked successful response
		GenerateContentResponse mockedResponse = GenerateContentResponse.newBuilder()
			.addCandidates(Candidate.newBuilder()
				.setContent(Content.newBuilder().addParts(Part.newBuilder().setText("Response").build()).build())
				.build())
			.build();

		given(this.mockGenerativeModel.generateContent(any(List.class)))
			.willThrow(new TransientAiException("Transient Error 1"))
			.willThrow(new TransientAiException("Transient Error 2"))
			.willReturn(mockedResponse);

		// Call the chat model
		ChatResponse result = this.chatModel.call(new Prompt("test prompt"));

		// Assertions
		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getContent()).isEqualTo("Response");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
	}

	@Test
	public void vertexAiGeminiChatNonTransientError() throws Exception {
		// Set up the mock GenerativeModel to throw a non-transient RuntimeException
		given(this.mockGenerativeModel.generateContent(any(List.class)))
			.willThrow(new RuntimeException("Non Transient Error"));

		// Assert that a RuntimeException is thrown when calling the chat model
		assertThrows(RuntimeException.class, () -> this.chatModel.call(new Prompt("test prompt")));
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
