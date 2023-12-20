/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.autoconfigure.ollama;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ollama Embedding autoconfiguration properties.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
@ConfigurationProperties(OllamaEmbeddingProperties.CONFIG_PREFIX)
public class OllamaEmbeddingProperties {

	public static final String CONFIG_PREFIX = "spring.ai.ollama.embedding";

	/**
	 * Enable Ollama Embedding Client. True by default.
	 */
	private boolean enabled = true;

	/**
	 * Base URL where Ollama API server is running.
	 */
	private String baseUrl = "http://localhost:11434";

	/**
	 * Ollama Embedding model name. Defaults to 'llama2'.
	 */
	private String model = "llama2";

	private Map<String, Object> options;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public void setOptions(Map<String, Object> clientOptions) {
		this.options = clientOptions;
	}

	public Map<String, Object> getOptions() {
		return options;
	}

}
