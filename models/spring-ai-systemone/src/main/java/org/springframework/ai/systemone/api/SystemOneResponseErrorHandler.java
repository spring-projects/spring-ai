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

package org.springframework.ai.systemone.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.systemone.api.exception.SystemOneApiException;
import org.springframework.ai.systemone.api.exception.SystemOneAuthenticationException;
import org.springframework.ai.systemone.api.exception.SystemOneBadRequestException;
import org.springframework.ai.systemone.api.exception.SystemOneErrorDetail;
import org.springframework.ai.systemone.api.exception.SystemOneInternalServerException;
import org.springframework.ai.systemone.api.exception.SystemOneNotFoundException;
import org.springframework.ai.systemone.api.exception.SystemOneOverloadedException;
import org.springframework.ai.systemone.api.exception.SystemOnePermissionDeniedException;
import org.springframework.ai.systemone.api.exception.SystemOneRateLimitException;
import org.springframework.ai.systemone.api.exception.SystemOneUnprocessableEntityException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Turns a System One error response into the matching {@link SystemOneApiException}
 * subclass, so that callers branch on an exception type rather than on a status code, and
 * so that the retry policy can tell a transient failure from a permanent one.
 *
 * <p>
 * The API documents 401, 422, 429 and the non-standard 529; the remaining mappings follow
 * the shape of the official SDKs. Confirmed against the live API: a rejected API key is a
 * 401, a missing one a 403, an unknown model or a malformed question a 400, a body that
 * fails request validation a 422, and only an unrouted path is a 404.
 *
 * <p>
 * The body of every one of those is a {@code detail} envelope, read by
 * {@link SystemOneErrorDetail} so that the exception message states what the server said
 * rather than the raw JSON.
 *
 * <p>
 * Registered by {@link SystemOneApi} for every error status, through
 * {@code RestClient.Builder#defaultStatusHandler(Predicate, ErrorHandler)}.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public class SystemOneResponseErrorHandler implements RestClient.ResponseSpec.ErrorHandler {

	@Override
	public void handle(HttpRequest request, ClientHttpResponse response) throws IOException {
		int status = response.getStatusCode().value();
		HttpHeaders headers = response.getHeaders();
		String body = readBody(response);
		String endpoint = "%s %s".formatted(request.getMethod().name(), request.getURI());

		throw toException(status, body, headers, endpoint);
	}

	/**
	 * Maps a status code onto the exception that represents it.
	 * @param status the HTTP status code
	 * @param body the raw response body, or {@code null}
	 * @param headers the response headers
	 * @param endpoint the method and URL of the request
	 * @return the exception to throw
	 */
	public SystemOneApiException toException(int status, @Nullable String body, HttpHeaders headers, String endpoint) {
		String message = buildMessage(status, body, headers, endpoint);

		if (status == SystemOneOverloadedException.OVERLOADED_STATUS) {
			return new SystemOneOverloadedException(message, status, body, headers, endpoint);
		}
		return switch (status) {
			case 400 -> new SystemOneBadRequestException(message, status, body, headers, endpoint);
			case 401 -> new SystemOneAuthenticationException(message, status, body, headers, endpoint);
			case 403 -> new SystemOnePermissionDeniedException(message, status, body, headers, endpoint);
			case 404 -> new SystemOneNotFoundException(message, status, body, headers, endpoint);
			case 422 -> new SystemOneUnprocessableEntityException(message, status, body, headers, endpoint);
			case 429 ->
				new SystemOneRateLimitException(message, status, body, headers, endpoint, retryAfterMs(headers));
			default -> status >= 500 ? new SystemOneInternalServerException(message, status, body, headers, endpoint)
					: new SystemOneApiException(message, status, body, headers, endpoint);
		};
	}

	/**
	 * Reads the wait the server asked for, preferring the millisecond precision
	 * {@code retry-after-ms} header over the standard {@code Retry-After} seconds header.
	 * A number of seconds too large to state in milliseconds reads as
	 * {@link Long#MAX_VALUE}.
	 * @param headers the response headers
	 * @return the wait in milliseconds, or {@code null} when the server stated none
	 */
	public static @Nullable Long retryAfterMs(HttpHeaders headers) {
		Long millis = parseLong(headers.getFirst(SystemOneRateLimitException.RETRY_AFTER_MS_HEADER));
		if (millis != null) {
			return millis;
		}
		Long seconds = parseLong(headers.getFirst(HttpHeaders.RETRY_AFTER));
		if (seconds == null) {
			return null;
		}
		// Saturate rather than wrap: a wrapped product is negative, or an arbitrary wait.
		return seconds > Long.MAX_VALUE / 1000 ? Long.MAX_VALUE : seconds * 1000;
	}

	private static @Nullable Long parseLong(@Nullable String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		try {
			long parsed = Long.parseLong(value.trim());
			return parsed < 0 ? null : parsed;
		}
		catch (NumberFormatException ex) {
			// A Retry-After given as an HTTP date is valid but not actionable here.
			return null;
		}
	}

	private String buildMessage(int status, @Nullable String body, HttpHeaders headers, String endpoint) {
		StringBuilder message = new StringBuilder();
		message.append(endpoint).append(" failed with ").append(status);

		HttpStatus resolved = HttpStatus.resolve(status);
		if (resolved != null) {
			message.append(' ').append(resolved.getReasonPhrase());
		}
		String requestId = headers.getFirst(SystemOneApiException.REQUEST_ID_HEADER);
		if (StringUtils.hasText(requestId)) {
			message.append(" (request id ").append(requestId).append(')');
		}
		// Prefer the server's own description over the raw JSON envelope, falling back to
		// the body when it is not a shape we recognise. The raw text stays on the
		// exception as body() either way.
		SystemOneErrorDetail detail = SystemOneErrorDetail.parse(body);
		String described = (detail == null) ? null : detail.toString();
		if (StringUtils.hasText(described)) {
			message.append(": ").append(described);
		}
		else if (StringUtils.hasText(body)) {
			message.append(": ").append(body);
		}
		return message.toString();
	}

	private @Nullable String readBody(ClientHttpResponse response) {
		try {
			String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
			return StringUtils.hasText(body) ? body : null;
		}
		catch (IOException ex) {
			return null;
		}
	}

}
