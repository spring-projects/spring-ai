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

package org.springframework.ai.huggingface.api.common;

import org.springframework.ai.observation.conventions.AiProvider;

/**
 * Common value constants for HuggingFace API.
 *
 * @author Myeongdeok Kang
 */
public final class HuggingfaceApiConstants {

	/**
	 * Default base URL for HuggingFace Chat API (OpenAI-compatible endpoint).
	 */
	public static final String DEFAULT_CHAT_BASE_URL = "https://router.huggingface.co/v1";

	/**
	 * Default base URL for HuggingFace Embedding API (Feature Extraction endpoint).
	 */
	public static final String DEFAULT_EMBEDDING_BASE_URL = "https://router.huggingface.co/hf-inference/models";

	/**
	 * Provider name for observation and metrics.
	 */
	public static final String PROVIDER_NAME = AiProvider.HUGGINGFACE.value();

	private HuggingfaceApiConstants() {
		// Prevent instantiation
	}

}
