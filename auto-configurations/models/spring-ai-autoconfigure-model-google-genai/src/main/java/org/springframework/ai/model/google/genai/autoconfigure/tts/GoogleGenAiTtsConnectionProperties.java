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

package org.springframework.ai.model.google.genai.autoconfigure.tts;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection properties for Google GenAI TTS.
 *
 * @author Alexandros Pappas
 * @since 1.1.0
 */
@ConfigurationProperties(GoogleGenAiTtsConnectionProperties.CONFIG_PREFIX)
public class GoogleGenAiTtsConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.google.genai";

	/**
	 * Google GenAI API Key for TTS.
	 */
	private String apiKey;

	/**
	 * Base URL for the Google GenAI TTS API.
	 */
	private String baseUrl = "https://generativelanguage.googleapis.com";

	public String getApiKey() {
		return this.apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getBaseUrl() {
		return this.baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

}
