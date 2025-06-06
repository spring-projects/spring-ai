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

package org.springframework.ai.ollama.api.common;

import org.springframework.ai.observation.conventions.AiProvider;

/**
 * Common value constants for Ollama api.
 *
 * @author Jonghoon Park
 * @author lambochen
 */
public final class OllamaApiConstants {

	public static final String DEFAULT_BASE_URL = "http://localhost:11434";

	public static final String PROVIDER_NAME = AiProvider.OLLAMA.value();

	public static final String DEFAULT_CHAT_PATH = "/api/chat";

	public static final String DEFAULT_EMBED_PATH = "/api/embed";

	public static final String DEFAULT_LIST_MODELS_PATH = "/api/tags";

	public static final String DEFAULT_SHOW_MODEL_PATH = "/api/show";

	public static final String DEFAULT_COPY_MODEL_PATH = "/api/copy";

	public static final String DEFAULT_DELETE_MODEL_PATH = "/api/delete";

	public static final String DEFAULT_PULL_MODEL_PATH = "/api/pull";

	private OllamaApiConstants() {

	}

}
