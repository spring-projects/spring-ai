/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.openai.api.common;

/**
 * Thrown on 4xx client errors, such as 401 - Incorrect API key provided, 401 - You must
 * be a member of an organization to use the API, 429 - Rate limit reached for requests,
 * 429 - You exceeded your current quota , please check your plan and billing details.
 */
public class OpenAiApiClientErrorException extends RuntimeException {

	public OpenAiApiClientErrorException(String message) {
		super(message);
	}

	public OpenAiApiClientErrorException(String message, Throwable cause) {
		super(message, cause);
	}

}