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

package org.springframework.ai.retry;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RetryUtils test
 *
 * @author lance
 */
class RetryUtilsTests {

	/**
	 * valid http 4xx
	 * @throws IOException ex
	 */
	@Test
	void handleError4xx() throws IOException {
		try (ClientHttpResponse response = mock(ClientHttpResponse.class)) {
			URI url = mock(URI.class);
			HttpMethod method = HttpMethod.POST;

			when(response.getStatusCode()).thenReturn(HttpStatus.BAD_REQUEST);
			when(response.getBody())
				.thenReturn(new ByteArrayInputStream("Bad request".getBytes(StandardCharsets.UTF_8)));

			assertThrows(NonTransientAiException.class,
					() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER.handleError(url, method, response));
		}
	}

	/**
	 * valid http 5xx
	 * @throws IOException ex
	 */
	@Test
	void handleError5xx() throws IOException {
		try (ClientHttpResponse response = mock(ClientHttpResponse.class)) {
			URI url = mock(URI.class);
			HttpMethod method = HttpMethod.POST;
			when(response.getStatusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
			when(response.getBody())
				.thenReturn(new ByteArrayInputStream("Server error".getBytes(StandardCharsets.UTF_8)));

			assertThrows(TransientAiException.class,
					() -> RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER.handleError(url, method, response));
		}
	}

	/**
	 * valid not error
	 * @throws IOException ex
	 */
	@Test
	void hasError() throws IOException {
		try (ClientHttpResponse response = mock(ClientHttpResponse.class)) {
			when(response.getStatusCode()).thenReturn(HttpStatus.OK);
			when(response.getBody()).thenReturn(new ByteArrayInputStream("success".getBytes(StandardCharsets.UTF_8)));

			assertFalse(RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER.hasError(response));
		}
	}

	@Test
	void shortRetryTemplateRetries() {
		AtomicInteger counter = new AtomicInteger(0);
		RetryTemplate template = RetryUtils.SHORT_RETRY_TEMPLATE;

		assertThrows(RetryException.class, () -> template.execute(() -> {
			counter.incrementAndGet();
			throw new TransientAiException("test fail");
		}));

		assertEquals(11, counter.get());
	}

	@Test
	void shortRetryTemplateSucceedsBeforeMaxAttempts() throws RetryException {
		AtomicInteger counter = new AtomicInteger(0);
		RetryTemplate template = RetryUtils.SHORT_RETRY_TEMPLATE;

		String result = template.execute(() -> {
			if (counter.incrementAndGet() < 5) {
				throw new TransientAiException("test fail");
			}
			return "success";
		});

		assertEquals(5, counter.get());
		assertEquals("success", result);
	}

}
