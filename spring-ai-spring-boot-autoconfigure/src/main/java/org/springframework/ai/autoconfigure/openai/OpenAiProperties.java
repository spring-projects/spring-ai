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

package org.springframework.ai.autoconfigure.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

import static org.springframework.ai.autoconfigure.openai.OpenAiProperties.CONFIG_PREFIX;

@ConfigurationProperties(CONFIG_PREFIX)
public class OpenAiProperties {

	public static final String CONFIG_PREFIX = "spring.ai.openai";

	private Double temperature = 0.7;

	private Duration duration = Duration.ofSeconds(60);

	private String apiKey;

	private String model = "gpt-3.5-turbo";

	private String baseUrl = "https://api.openai.com";

	private String embeddingModel = "text-embedding-ada-002";

	private String embeddingBaseUrl;

	private String embeddingApiKey;

	public String getApiKey() {
		return this.apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public Double getTemperature() {
		return this.temperature;
	}

	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	public Duration getDuration() {
		return this.duration;
	}

	public void setDuration(Duration duration) {
		this.duration = duration;
	}

	public String getBaseUrl() {
		return this.baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getEmbeddingModel() {
		return this.embeddingModel;
	}

	public void setEmbeddingModel(String embeddingModel) {
		this.embeddingModel = embeddingModel;
	}

	public void setEmbeddingBaseUrl(String embeddingBaseUrl) {
		this.embeddingBaseUrl = embeddingBaseUrl;
	}

	public String getEmbeddingBaseUrl() {
		return StringUtils.hasText(this.embeddingBaseUrl) ? this.embeddingBaseUrl : this.baseUrl;
	}

	public String getEmbeddingApiKey() {
		return StringUtils.hasText(this.embeddingApiKey) ? this.embeddingApiKey : this.apiKey;
	}

	public void setEmbeddingApiKey(String embeddingApiKey) {
		this.embeddingApiKey = embeddingApiKey;
	}

}
