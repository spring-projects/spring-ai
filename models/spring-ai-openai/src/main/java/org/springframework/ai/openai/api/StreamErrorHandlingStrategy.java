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
 * Strategy for handling JSON parsing errors in streaming chat completions. This is
 * particularly useful when dealing with LLMs that may return malformed JSON, such as
 * Qwen3-8B or other custom models.
 *
 * @author Liu Guodong
 * @since 1.0.0
 */
public enum StreamErrorHandlingStrategy {

	/**
	 * Skip invalid chunks and continue processing the stream. This is the default and
	 * recommended strategy for production use. Invalid chunks are logged but do not
	 * interrupt the stream.
	 */
	SKIP,

	/**
	 * Fail immediately when encountering an invalid chunk. The error is propagated
	 * through the reactive stream, terminating the stream processing.
	 */
	FAIL_FAST,

	/**
	 * Log the error and continue processing. Similar to SKIP but with more detailed
	 * logging. Use this for debugging or when you want to monitor the frequency of
	 * parsing errors.
	 */
	LOG_AND_CONTINUE

}
