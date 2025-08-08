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

package org.springframework.ai.tool.consent.exception;

/**
 * Exception thrown when user consent is required but denied for tool execution.
 *
 * @author Hyunjoon Park
 * @since 1.0.0
 */
public class ConsentDeniedException extends RuntimeException {

	/**
	 * Constructs a new consent denied exception with the specified detail message.
	 * @param message the detail message
	 */
	public ConsentDeniedException(String message) {
		super(message);
	}

	/**
	 * Constructs a new consent denied exception with the specified detail message and
	 * cause.
	 * @param message the detail message
	 * @param cause the cause
	 */
	public ConsentDeniedException(String message, Throwable cause) {
		super(message, cause);
	}

}
