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
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;

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
		this.retryTemplate.setRetryListener(this.retryListener);

		this.chatModel = new TestVertexAiGeminiChatModel(this.vertexAI,
				VertexAiGeminiChatOptions.builder()
					.temperature(0.7)
					.topP(1.0)
					.model(VertexAiGeminiChatModel.ChatModel.GEMINI_2_0_FLASH.getValue())
					.build(),
				this.retryTemplate);

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
		assertThat(result.getResult().getOutput().getText()).isEqualTo("Response");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
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

	@Test
	public void vertexAiGeminiChatSuccessOnFirstAttempt() throws Exception {
		// Create a mocked successful response
		GenerateContentResponse mockedResponse = GenerateContentResponse.newBuilder()
			.addCandidates(Candidate.newBuilder()
				.setContent(Content.newBuilder()
					.addParts(Part.newBuilder().setText("First Attempt Success").build())
					.build())
				.build())
			.build();

		given(this.mockGenerativeModel.generateContent(any(List.class))).willReturn(mockedResponse);

		// Call the chat model
		ChatResponse result = this.chatModel.call(new Prompt("test prompt"));

		// Assertions
		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getText()).isEqualTo("First Attempt Success");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(0); // No retries
																			// needed
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(0);
	}

	@Test
	public void vertexAiGeminiChatWithEmptyResponse() throws Exception {
		// Test handling of empty response after retries
		GenerateContentResponse emptyResponse = GenerateContentResponse.newBuilder().build();

		given(this.mockGenerativeModel.generateContent(any(List.class)))
			.willThrow(new TransientAiException("Temporary issue"))
			.willReturn(emptyResponse);

		// Call the chat model
		ChatResponse result = this.chatModel.call(new Prompt("test prompt"));

		// Should handle empty response gracefully
		assertThat(result).isNotNull();
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(1);
	}

	@Test
	public void vertexAiGeminiChatMaxRetriesExceeded() throws Exception {
		// Test that after max retries, the exception is propagated
		given(this.mockGenerativeModel.generateContent(any(List.class)))
			.willThrow(new TransientAiException("Persistent Error"))
			.willThrow(new TransientAiException("Persistent Error"))
			.willThrow(new TransientAiException("Persistent Error"))
			.willThrow(new TransientAiException("Persistent Error"));

		// Should throw the last TransientAiException after exhausting retries
		assertThrows(TransientAiException.class, () -> this.chatModel.call(new Prompt("test prompt")));
		// Verify retry attempts were made
		assertThat(this.retryListener.onErrorRetryCount).isGreaterThan(0);
	}

	@Test
	public void vertexAiGeminiChatWithMultipleCandidatesResponse() throws Exception {
		// Test response with multiple candidates
		GenerateContentResponse multiCandidateResponse = GenerateContentResponse.newBuilder()
			.addCandidates(Candidate.newBuilder()
				.setContent(Content.newBuilder().addParts(Part.newBuilder().setText("First candidate").build()).build())
				.build())
			.addCandidates(Candidate.newBuilder()
				.setContent(
						Content.newBuilder().addParts(Part.newBuilder().setText("Second candidate").build()).build())
				.build())
			.build();

		given(this.mockGenerativeModel.generateContent(any(List.class)))
			.willThrow(new TransientAiException("Temporary failure"))
			.willReturn(multiCandidateResponse);

		ChatResponse result = this.chatModel.call(new Prompt("test prompt"));

		assertThat(result).isNotNull();
		// Assuming the implementation uses the first candidate
		assertThat(result.getResult().getOutput().getText()).isEqualTo("First candidate");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
	}

	@Test
	public void vertexAiGeminiChatWithNullPrompt() throws Exception {
		// Test handling of null prompt
		Prompt prompt = null;
		assertThrows(Exception.class, () -> this.chatModel.call(prompt));

		// Should not trigger any retries for validation errors
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(0);
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(0);
	}

	@Test
	public void vertexAiGeminiChatWithEmptyPrompt() throws Exception {
		// Test handling of empty prompt
		GenerateContentResponse mockedResponse = GenerateContentResponse.newBuilder()
			.addCandidates(Candidate.newBuilder()
				.setContent(Content.newBuilder()
					.addParts(Part.newBuilder().setText("Response to empty prompt").build())
					.build())
				.build())
			.build();

		given(this.mockGenerativeModel.generateContent(any(List.class))).willReturn(mockedResponse);

		ChatResponse result = this.chatModel.call(new Prompt(""));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getText()).isEqualTo("Response to empty prompt");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(0);
	}

	@Test
	public void vertexAiGeminiChatAlternatingErrorsAndSuccess() throws Exception {
		// Test pattern of error -> success -> error -> success
		GenerateContentResponse successResponse = GenerateContentResponse.newBuilder()
			.addCandidates(Candidate.newBuilder()
				.setContent(Content.newBuilder()
					.addParts(Part.newBuilder().setText("Success after alternating errors").build())
					.build())
				.build())
			.build();

		given(this.mockGenerativeModel.generateContent(any(List.class)))
			.willThrow(new TransientAiException("First error"))
			.willThrow(new TransientAiException("Second error"))
			.willReturn(successResponse);

		ChatResponse result = this.chatModel.call(new Prompt("test prompt"));

		assertThat(result).isNotNull();
		assertThat(result.getResult().getOutput().getText()).isEqualTo("Success after alternating errors");
		assertThat(this.retryListener.onSuccessRetryCount).isEqualTo(1);
		assertThat(this.retryListener.onErrorRetryCount).isEqualTo(2);
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
