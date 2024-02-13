/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.autoconfigure.vertexai.palm2;

import org.springframework.ai.vertexai.palm2.api.VertexAiApi;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(VertexAiPalm2ConnectionProperties.CONFIG_PREFIX)
public class VertexAiPalm2ConnectionProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vertex.ai";

	/**
	 * Vertex AI PaLM API access key.
	 */
	private String apiKey;

	/**
	 * Vertex AI PaLM API base URL. Defaults to
	 * https://generativelanguage.googleapis.com/v1beta3
	 */
	private String baseUrl = VertexAiApi.DEFAULT_BASE_URL;

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
