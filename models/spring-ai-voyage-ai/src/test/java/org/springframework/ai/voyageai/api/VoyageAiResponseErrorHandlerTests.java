/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.voyageai.api;

import java.net.URI;

import org.junit.jupiter.api.Test;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class VoyageAiResponseErrorHandlerTests {

	private final VoyageAiResponseErrorHandler handler = new VoyageAiResponseErrorHandler();

	@Test
	void reportsTheDetailOfAClientError() {
		var response = new MockClientHttpResponse("{\"detail\": \"Model 'x' is not supported\"}".getBytes(),
				HttpStatus.BAD_REQUEST);
		assertThatExceptionOfType(NonTransientAiException.class)
			.isThrownBy(
					() -> this.handler.handleError(URI.create("https://api.voyageai.com"), HttpMethod.POST, response))
			.withMessage("400 - Model 'x' is not supported");
	}

	@Test
	void treatsAuthenticationFailureAsPermanent() {
		var response = new MockClientHttpResponse("{\"detail\": \"Provided API key is invalid.\"}".getBytes(),
				HttpStatus.UNAUTHORIZED);
		assertThatExceptionOfType(NonTransientAiException.class)
			.isThrownBy(
					() -> this.handler.handleError(URI.create("https://api.voyageai.com"), HttpMethod.POST, response))
			.withMessage("401 - Provided API key is invalid.");
	}

	@Test
	void retriesRateLimitingAndServerErrors() {
		var rateLimited = new MockClientHttpResponse("{\"detail\": \"Rate limit reached\"}".getBytes(),
				HttpStatus.TOO_MANY_REQUESTS);
		assertThatExceptionOfType(TransientAiException.class).isThrownBy(
				() -> this.handler.handleError(URI.create("https://api.voyageai.com"), HttpMethod.POST, rateLimited))
			.withMessage("429 - Rate limit reached");
		var serverError = new MockClientHttpResponse("{\"detail\": \"overloaded\"}".getBytes(),
				HttpStatus.SERVICE_UNAVAILABLE);
		assertThatExceptionOfType(TransientAiException.class).isThrownBy(
				() -> this.handler.handleError(URI.create("https://api.voyageai.com"), HttpMethod.POST, serverError));
	}

	@Test
	void aDelegateClassifiesTheErrorAndSeesTheExtractedMessage() {
		var seen = new java.util.concurrent.atomic.AtomicReference<String>();
		var handler = new VoyageAiResponseErrorHandler(new org.springframework.web.client.ResponseErrorHandler() {
			@Override
			public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
				return true;
			}

			@Override
			public void handleError(URI url, HttpMethod method,
					org.springframework.http.client.ClientHttpResponse response) throws java.io.IOException {
				seen.set(new String(response.getBody().readAllBytes()));
				throw new TransientAiException("delegate decided");
			}
		});
		var response = new MockClientHttpResponse("{\"detail\": \"too many tokens\"}".getBytes(),
				HttpStatus.BAD_REQUEST);

		assertThatExceptionOfType(TransientAiException.class)
			.isThrownBy(() -> handler.handleError(URI.create("https://api.voyageai.com"), HttpMethod.POST, response))
			.withMessage("delegate decided");
		assertThat(seen.get()).isEqualTo("too many tokens");
	}

	@Test
	void reportsABodyWithoutATextualDetailAsItIs() {
		assertThat(VoyageAiResponseErrorHandler.extractMessage("<html>bad gateway</html>"))
			.isEqualTo("<html>bad gateway</html>");
		assertThat(VoyageAiResponseErrorHandler.extractMessage("{\"detail\": [{\"msg\": \"field required\"}]}"))
			.isEqualTo("{\"detail\": [{\"msg\": \"field required\"}]}");
		assertThat(VoyageAiResponseErrorHandler.extractMessage("{\"error\": \"x\"}")).isEqualTo("{\"error\": \"x\"}");
	}

}
