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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResponseErrorHandler;

/**
 * Turns an error response of the Voyage AI API into an exception.
 *
 * <p>
 * Voyage AI reports errors as {@code {"detail": "<message>"}}. This handler extracts the
 * message, so that the exception carries the text Voyage AI gave instead of the raw JSON.
 * A body of any other shape is reported unchanged.
 *
 * <p>
 * Without a delegate, rate limiting ({@code 429}) and server errors ({@code 5xx}) are
 * {@link TransientAiException transient}, so a retry template retries them, and every
 * other client error is a {@link NonTransientAiException}. With a delegate, for example
 * the handler that the Spring AI retry auto-configuration builds from the
 * {@code spring.ai.retry.*} properties, the delegate decides, and it sees the extracted
 * message as the body of the response.
 *
 * @author Zoltan Fodi
 * @since 2.1.1
 */
public class VoyageAiResponseErrorHandler implements ResponseErrorHandler {

	private final @Nullable ResponseErrorHandler delegate;

	/**
	 * Create a handler that decides by itself which errors are transient.
	 */
	public VoyageAiResponseErrorHandler() {
		this(null);
	}

	/**
	 * Create a handler that hands the extracted message to another handler.
	 * @param delegate the handler that classifies the error, or {@code null} to classify
	 * it here
	 */
	public VoyageAiResponseErrorHandler(@Nullable ResponseErrorHandler delegate) {
		this.delegate = delegate;
	}

	@Override
	public boolean hasError(ClientHttpResponse response) throws IOException {
		return response.getStatusCode().isError();
	}

	@Override
	public void handleError(URI url, HttpMethod method, ClientHttpResponse response) throws IOException {
		HttpStatusCode status = response.getStatusCode();
		String message = extractMessage(StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8));
		if (this.delegate != null) {
			this.delegate.handleError(url, method, new MessageResponse(response, message));
			return;
		}
		if (status.is4xxClientError() && status.value() != 429) {
			throw new NonTransientAiException(status.value() + " - " + message);
		}
		throw new TransientAiException(status.value() + " - " + message);
	}

	/**
	 * Extract the {@code detail} message of a Voyage AI error body.
	 * @param body the raw response body
	 * @return the {@code detail} text, or the raw body when it has no textual
	 * {@code detail}
	 */
	static String extractMessage(String body) {
		try {
			Map<?, ?> parsed = JsonParser.fromJson(body, Map.class);
			Object detail = (parsed != null) ? parsed.get("detail") : null;
			if (detail instanceof String text && !text.isBlank()) {
				return text;
			}
		}
		catch (RuntimeException ex) {
			// not JSON: report the body as it is
		}
		return body;
	}

	/**
	 * The response that was received, with the extracted message as its body.
	 */
	private static final class MessageResponse implements ClientHttpResponse {

		private final ClientHttpResponse response;

		private final byte[] body;

		MessageResponse(ClientHttpResponse response, String message) {
			this.response = response;
			this.body = message.getBytes(StandardCharsets.UTF_8);
		}

		@Override
		public HttpStatusCode getStatusCode() throws IOException {
			return this.response.getStatusCode();
		}

		@Override
		public String getStatusText() throws IOException {
			return this.response.getStatusText();
		}

		@Override
		public HttpHeaders getHeaders() {
			return this.response.getHeaders();
		}

		@Override
		public InputStream getBody() {
			return new ByteArrayInputStream(this.body);
		}

		@Override
		public void close() {
			this.response.close();
		}

	}

}
