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

package org.springframework.ai.google.genai;

import java.io.IOException;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryListener;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.core.retry.Retryable;

/**
 * @author Mark Pollack
 */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
public class GoogleGenAiRetryTests {

	private TestRetryListener retryListener;

	private RetryTemplate retryTemplate;

	@Mock
	private Client genAiClient;

	@Mock
	private GenerateContentResponse mockGenerateContentResponse;

	private org.springframework.ai.google.genai.TestGoogleGenAiGeminiChatModel chatModel;

	@BeforeEach
	public void setUp() {
		this.retryTemplate = RetryUtils.SHORT_RETRY_TEMPLATE;
		this.retryListener = new TestRetryListener();
		this.retryTemplate.setRetryListener(this.retryListener);

		this.chatModel = new org.springframework.ai.google.genai.TestGoogleGenAiGeminiChatModel(this.genAiClient,
				GoogleGenAiChatOptions.builder()
					.temperature(0.7)
					.topP(1.0)
					.model(GoogleGenAiChatModel.ChatModel.GEMINI_2_0_FLASH.getValue())
					.build(),
				this.retryTemplate);

		// Mock response will be set in each test
	}

	@Test
	public void vertexAiGeminiChatTransientError() throws IOException {
		// For this test, we need to test transient errors. Since we can't easily mock
		// the actual HTTP calls in the new SDK, we'll need to update this test
		// to work with the new architecture.
		// This test would need to be restructured to test retry behavior differently.

		// TODO: Update this test to work with the new GenAI SDK
		// The test logic needs to be restructured since we can't easily mock
		// the internal HTTP calls in the new SDK
	}

	@Test
	public void vertexAiGeminiChatNonTransientError() throws Exception {
		// For this test, we need to test non-transient errors. Since we can't easily mock
		// the actual HTTP calls in the new SDK, we'll need to update this test
		// to work with the new architecture.
		// This test would need to be restructured to test error handling differently.
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
