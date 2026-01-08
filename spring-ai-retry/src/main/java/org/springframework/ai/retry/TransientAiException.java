/*
 * Copyright 2023-2024 the original author or authors.
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

import org.jspecify.annotations.Nullable;

/**
 * Root of the hierarchy of Model access exceptions that are considered transient - where
 * a previously failed operation might be able to succeed when the operation is retried
 * without any intervention.
 *
 * @author Christian Tzolov
 * @since 0.8.1
 */
public class TransientAiException extends RuntimeException {

	/**
	 * Constructor with message.
	 * @param message the exception message
	 */
	public TransientAiException(final String message) {
		super(message);
	}

	/**
	 * Constructor with message and cause.
	 * @param message the exception message
	 * @param cause the exception cause
	 */
	public TransientAiException(final String message, final @Nullable Throwable cause) {
		super(message, cause);
	}

}
