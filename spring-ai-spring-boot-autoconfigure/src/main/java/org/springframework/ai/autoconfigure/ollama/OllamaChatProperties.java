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

package org.springframework.ai.autoconfigure.ollama;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ollama Chat autoconfiguration properties.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
@ConfigurationProperties(OllamaChatProperties.CONFIG_PREFIX)
public class OllamaChatProperties {

	public static final String CONFIG_PREFIX = "spring.ai.ollama.chat";

	/**
	 * Base URL where Ollama API server is running.
	 */
	private String baseUrl = "http://localhost:11434";

	/**
	 * Enable Ollama Chat Client. True by default.
	 */
	private boolean enabled = true;

	/**
	 * Ollama Chat model name. Defaults to 'llama2'.
	 */
	private String model = "llama2";

	/**
	 * (optional) Use a lower value to decrease randomness in the response. Defaults to
	 * 0.7.
	 */
	private Float temperature = 0.8f;

	/**
	 * (optional) The maximum cumulative probability of tokens to consider when sampling.
	 * The model uses combined Top-k and nucleus sampling. Nucleus sampling considers the
	 * smallest set of tokens whose probability sum is at least topP.
	 */
	private Float topP;

	/**
	 * Max number or responses to generate.
	 */
	private Integer topK;

	private Map<String, Object> options;

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

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

	public Float getTemperature() {
		return temperature;
	}

	public void setTemperature(Float temperature) {
		this.temperature = temperature;
	}

	public Float getTopP() {
		return topP;
	}

	public void setTopP(Float topP) {
		this.topP = topP;
	}

	public Integer getTopK() {
		return topK;
	}

	public void setTopK(Integer maxTokens) {
		this.topK = maxTokens;
	}

	public void setOptions(Map<String, Object> options) {
		this.options = options;
	}

	public Map<String, Object> getOptions() {
		return options;
	}

}
