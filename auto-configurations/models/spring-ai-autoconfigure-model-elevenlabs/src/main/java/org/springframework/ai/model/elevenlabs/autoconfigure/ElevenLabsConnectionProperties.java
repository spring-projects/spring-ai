/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.model.elevenlabs.autoconfigure;

import org.springframework.ai.elevenlabs.api.ElevenLabsApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.http.client.autoconfigure.HttpClientSettingsProperties;

/**
 * Configuration properties for the ElevenLabs API connection.
 *
 * @author Alexandros Pappas
 */
@ConfigurationProperties(ElevenLabsConnectionProperties.CONFIG_PREFIX)
public class ElevenLabsConnectionProperties extends HttpClientSettingsProperties {

	public static final String CONFIG_PREFIX = "spring.ai.elevenlabs";

	/**
	 * ElevenLabs API access key.
	 */
	private String apiKey;

	/**
	 * ElevenLabs API base URL.
	 */
	private String baseUrl = ElevenLabsApi.DEFAULT_BASE_URL;

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
