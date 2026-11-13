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

import java.time.Duration;

import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpHeaders;

/**
 * Raised on HTTP 429. The account has exceeded its rate limit; back off and retry. The
 * server may state how long to wait, in which case {@link #retryAfter()} carries it and a
 * policy wrapped by {@code SystemOneRetryUtils.honouringRetryAfter} waits that long.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public class SystemOneRateLimitException extends SystemOneApiException {

	/** Non-standard header used by the API to state the wait in milliseconds. */
	public static final String RETRY_AFTER_MS_HEADER = "retry-after-ms";

	private final @Nullable Long retryAfterMs;

	public SystemOneRateLimitException(String message, int status, @Nullable String body, HttpHeaders headers,
			String endpoint, @Nullable Long retryAfterMs) {
		super(message, status, body, headers, endpoint);
		this.retryAfterMs = retryAfterMs;
	}

	/**
	 * @return the server's requested wait in milliseconds, or {@code null} when it did
	 * not state one
	 */
	public @Nullable Long retryAfterMs() {
		return this.retryAfterMs;
	}

	/**
	 * @return the server's requested wait, or {@code null} when it did not state one
	 */
	public @Nullable Duration retryAfter() {
		return this.retryAfterMs == null ? null : Duration.ofMillis(this.retryAfterMs);
	}

}
