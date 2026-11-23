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

package org.springframework.ai.openai.responses;

import org.jspecify.annotations.Nullable;

/**
 * Thrown when the Responses API reports a failed response, or emits an {@code error}
 * event mid-stream. Unlike a transport failure, this means the request reached the model
 * and the model run itself did not complete.
 *
 * @author Dimitar Proynov
 * @since 2.1.0
 */
public class OpenAiResponsesException extends RuntimeException {

	private final @Nullable String code;

	public OpenAiResponsesException(@Nullable String code, String message) {
		super(code != null ? "[" + code + "] " + message : message);
		this.code = code;
	}

	/**
	 * Return the OpenAI error code, or {@code null} if the error carried none.
	 */
	public @Nullable String getCode() {
		return this.code;
	}

}
