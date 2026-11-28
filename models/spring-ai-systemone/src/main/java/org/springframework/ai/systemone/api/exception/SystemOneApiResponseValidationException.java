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

import org.jspecify.annotations.Nullable;

import org.springframework.http.HttpHeaders;

/**
 * Raised when a successful HTTP response is missing data the client requires, for example
 * an empty body or an absent {@code answers} map.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public class SystemOneApiResponseValidationException extends SystemOneApiException {

	private final @Nullable String fieldPath;

	public SystemOneApiResponseValidationException(String message, int status, @Nullable String body,
			HttpHeaders headers, String endpoint, @Nullable String fieldPath) {
		super(message, status, body, headers, endpoint);
		this.fieldPath = fieldPath;
	}

	/**
	 * @return the dotted path to the offending field, or {@code null} when the whole body
	 * was missing
	 */
	public @Nullable String fieldPath() {
		return this.fieldPath;
	}

}
