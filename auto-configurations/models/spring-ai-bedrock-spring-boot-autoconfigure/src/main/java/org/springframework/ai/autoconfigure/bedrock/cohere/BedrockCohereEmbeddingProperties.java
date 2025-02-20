/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.autoconfigure.bedrock.cohere;

import org.springframework.ai.bedrock.cohere.BedrockCohereEmbeddingOptions;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingModel;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingRequest;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingRequest.InputType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Bedrock Cohere Embedding autoconfiguration properties.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
@ConfigurationProperties(BedrockCohereEmbeddingProperties.CONFIG_PREFIX)
public class BedrockCohereEmbeddingProperties {

	public static final String CONFIG_PREFIX = "spring.ai.bedrock.cohere.embedding";

	/**
	 * Enable Bedrock Cohere Embedding Model. False by default.
	 */
	private boolean enabled = false;

	/**
	 * Bedrock Cohere Embedding generative name. Defaults to
	 * 'cohere.embed-multilingual-v3'.
	 */
	private String model = CohereEmbeddingModel.COHERE_EMBED_MULTILINGUAL_V3.id();

	@NestedConfigurationProperty
	private BedrockCohereEmbeddingOptions options = BedrockCohereEmbeddingOptions.builder()
		.inputType(InputType.SEARCH_DOCUMENT)
		.truncate(CohereEmbeddingRequest.Truncate.NONE)
		.build();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public BedrockCohereEmbeddingOptions getOptions() {
		return this.options;
	}

	public void setOptions(BedrockCohereEmbeddingOptions options) {
		this.options = options;
	}

}
