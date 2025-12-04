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

package org.springframework.ai.embedding;

import org.springframework.ai.document.Document;

/**
 * {@link RuntimeException} thrown when the token count of the provided content exceeds
 * the configured maximum.
 *
 * @author John Blum
 * @see IllegalArgumentException
 * @since 1.1.0
 */
@SuppressWarnings("unused")
public class MaxTokenCountExceededException extends IllegalArgumentException {

	public static MaxTokenCountExceededException because(Document document, int tokenCount, int maxTokenCount) {
		String message = "Tokens [%d] from Document [%s] exceeds the configured maximum number of input tokens allowed [%d]"
			.formatted(tokenCount, document.getId(), maxTokenCount);
		return new MaxTokenCountExceededException(message);
	}

	public MaxTokenCountExceededException() {

	}

	public MaxTokenCountExceededException(String message) {
		super(message);
	}

	public MaxTokenCountExceededException(Throwable cause) {
		super(cause);
	}

	public MaxTokenCountExceededException(String message, Throwable cause) {
		super(message, cause);
	}

}
