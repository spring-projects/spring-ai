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

package org.springframework.ai.openai.api;

/**
 * Exception thrown when a ChatCompletionChunk cannot be parsed from streaming response.
 * This typically occurs when the LLM returns malformed JSON.
 *
 * @author Liu Guodong
 * @since 1.0.0
 */
public class ChatCompletionParseException extends RuntimeException {

	private final String rawContent;

	/**
	 * Constructs a new ChatCompletionParseException.
	 * @param message the detail message
	 * @param rawContent the raw content that failed to parse
	 * @param cause the cause of the parsing failure
	 */
	public ChatCompletionParseException(String message, String rawContent, Throwable cause) {
		super(message, cause);
		this.rawContent = rawContent;
	}

	/**
	 * Returns the raw content that failed to parse.
	 * @return the raw content string
	 */
	public String getRawContent() {
		return this.rawContent;
	}

}
