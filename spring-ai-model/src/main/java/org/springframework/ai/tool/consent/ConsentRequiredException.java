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

package org.springframework.ai.tool.consent;

/**
 * Exception thrown when a tool requires user consent but consent was not granted.
 *
 * @author Assistant
 * @since 1.0.0
 */
public class ConsentRequiredException extends RuntimeException {

	public ConsentRequiredException(String message) {
		super(message);
	}

	public ConsentRequiredException(String message, Throwable cause) {
		super(message, cause);
	}

}
