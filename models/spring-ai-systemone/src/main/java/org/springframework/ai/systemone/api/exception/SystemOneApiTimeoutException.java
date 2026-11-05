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

/**
 * Raised when a request exceeds its configured timeout.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public class SystemOneApiTimeoutException extends SystemOneApiConnectionException {

	private final @Nullable Duration timeout;

	public SystemOneApiTimeoutException(String message, @Nullable Duration timeout, @Nullable Throwable cause) {
		super(message, cause);
		this.timeout = timeout;
	}

	/**
	 * @return the timeout that was exceeded, or {@code null} when it is not known
	 */
	public @Nullable Duration timeout() {
		return this.timeout;
	}

}
