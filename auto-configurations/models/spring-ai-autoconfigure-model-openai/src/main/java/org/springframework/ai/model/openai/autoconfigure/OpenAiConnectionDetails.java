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

package org.springframework.ai.model.openai.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.autoconfigure.service.connection.ConnectionDetails;

/**
 * Details required to establish a connection to an OpenAI-compatible API.
 *
 * @author Thomas Vitale
 * @since 2.1.0
 */
public interface OpenAiConnectionDetails extends ConnectionDetails {

	/**
	 * The base URL of the OpenAI-compatible API, or {@code null} to let the client
	 * resolve its default (for example, from the {@code OPENAI_BASE_URL} environment
	 * variable).
	 * @return the base URL
	 */
	@Nullable String getBaseUrl();

	/**
	 * The API key used to authenticate against the OpenAI-compatible API. An empty string
	 * is the deliberate no-auth signal, while {@code null} means "not configured" and
	 * lets the client resolve a key from the environment.
	 * @return the API key
	 */
	@Nullable String getApiKey();

}
