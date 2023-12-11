/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.client;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.prompt.Prompt;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Christian Tzolov
 */
public class RetryAiClientTests {

	private class TestRetryListener implements RetryListener {

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

	private RetryTemplate retryTemplate;

	@BeforeEach
	public void beforeEach() {
		retryTemplate = RetryTemplate.builder()
			.maxAttempts(3)
			.exponentialBackoff(Duration.ofMillis(100), 5, Duration.ofSeconds(60))
			.build();
		retryListener = new TestRetryListener();
		retryTemplate.registerListener(retryListener);
	}

	@Test
	public void retryGenerate() {

		AiResponse expectedResponse = new AiResponse(List.of(new Generation("text")));

		AiClient delegate = mock(AiClient.class);

		when(delegate.generate(isA(Prompt.class))).thenThrow(new RuntimeException())
			.thenThrow(new RuntimeException())
			.thenReturn(expectedResponse);

		RetryAiClient client = new RetryAiClient(retryTemplate, delegate);

		var result = client.generate(new Prompt("text"));

		assertThat(result).isNotNull();
		assertThat(result).isSameAs(expectedResponse);
		assertThat(retryListener.onSuccessRetryCount).isEqualTo(2);
		assertThat(retryListener.onErrorRetryCount).isEqualTo(2);
	}

}
