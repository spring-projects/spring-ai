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

package org.springframework.ai.autoconfigure.azure.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(AzureOpenAiProperties.CONFIG_PREFIX)
public class AzureOpenAiProperties {

	// TODO look into Spring Cloud Azure project for credentials as well as
	// e.g. com.azure.core.credential.AzureKeyCredential
	public static final String CONFIG_PREFIX = "spring.ai.azure.openai";

	private String apiKey;

	private String endpoint;

	private Double temperature = 0.7;

	private String model = "gpt-35-turbo";

	private String embeddingModel = "text-embedding-ada-002";

	public String getEndpoint() {
		return endpoint;
	}

	/**
	 * Sets the service endpoint that will be connected to by clients.
	 * @param endpoint The URL of the service endpoint
	 */
	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public Double getTemperature() {
		return temperature;
	}

	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getApiKey() {
		return apiKey;
	}

	public String getEmbeddingModel() {
		return embeddingModel;
	}

	public void setEmbeddingModel(String embeddingModel) {
		this.embeddingModel = embeddingModel;
	}

}
