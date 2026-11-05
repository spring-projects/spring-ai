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

package org.springframework.ai.systemone.api.exception;

import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpHeaders;

/**
 * A failure reported by the System One API as an HTTP error response.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public class SystemOneApiException extends SystemOneException {

	/** The header carrying the id to quote when reporting a problem. */
	public static final String REQUEST_ID_HEADER = "x-typesafe-request-id";

	private final int status;

	private final @Nullable String body;

	private final HttpHeaders headers;

	private final String endpoint;

	/**
	 * Parsed from {@link #body} rather than taken as a constructor argument, so that the
	 * subclasses keep their signatures. Not serialized: the raw body is, and the detail
	 * can be parsed again from it.
	 */
	private final transient @Nullable SystemOneErrorDetail errorDetail;

	public SystemOneApiException(String message, int status, @Nullable String body, HttpHeaders headers,
			String endpoint) {
		super(message);
		this.status = status;
		this.body = body;
		this.headers = headers;
		this.endpoint = endpoint;
		this.errorDetail = SystemOneErrorDetail.parse(body);
	}

	/**
	 * @return the HTTP status code of the response
	 */
	public int status() {
		return this.status;
	}

	/**
	 * @return the raw response body, or {@code null} when the response had none
	 */
	public @Nullable String body() {
		return this.body;
	}

	/**
	 * @return the response headers
	 */
	public HttpHeaders headers() {
		return this.headers;
	}

	/**
	 * @return the method and URL of the request, without credentials
	 */
	public String endpoint() {
		return this.endpoint;
	}

	/**
	 * @return the {@code x-typesafe-request-id} header, or {@code null} when absent
	 */
	public @Nullable String requestId() {
		return this.headers.getFirst(REQUEST_ID_HEADER);
	}

	/**
	 * The {@code detail} of the error body.
	 * @return the parsed detail, or {@code null} when the body was absent or did not
	 * carry one
	 * @see SystemOneErrorDetail
	 */
	public @Nullable SystemOneErrorDetail errorDetail() {
		return this.errorDetail;
	}

	/**
	 * @return the machine-readable error kind, for example {@code authentication_error},
	 * or {@code null} when the body stated none
	 */
	public @Nullable String errorType() {
		SystemOneErrorDetail detail = errorDetail();
		return detail == null ? null : detail.errorType();
	}

	/**
	 * The server's own description of the failure, as opposed to {@link #getMessage()},
	 * which also names the endpoint and status.
	 * @return the message from the error body, or {@code null} when it carried none
	 */
	public @Nullable String errorMessage() {
		SystemOneErrorDetail detail = errorDetail();
		return detail == null ? null : detail.message();
	}

	/**
	 * @return the per-field validation failures, empty unless this is a 422
	 */
	public List<SystemOneErrorDetail.ValidationError> validationErrors() {
		SystemOneErrorDetail detail = errorDetail();
		return detail == null ? List.of() : detail.validationErrors();
	}

}
