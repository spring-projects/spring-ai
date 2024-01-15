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

package org.springframework.ai.autoconfigure.bedrock.cohere;

import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingModel;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingRequest;
import org.springframework.ai.bedrock.cohere.api.CohereEmbeddingBedrockApi.CohereEmbeddingRequest.InputType;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
	 * Enable Bedrock Cohere Embedding Client. False by default.
	 */
	private boolean enabled = false;

	/**
	 * Bedrock Cohere Embedding generative name. Defaults to
	 * 'cohere.embed-multilingual-v3'.
	 */
	private String model = CohereEmbeddingModel.COHERE_EMBED_MULTILINGUAL_V1.id();

	/**
	 * Prepends special tokens to differentiate each type from one another. You should not
	 * mix different types together, except when mixing types for for search and
	 * retrieval. In this case, embed your corpus with the search_document type and
	 * embedded queries with type search_query type.
	 */
	private InputType inputType = InputType.SEARCH_DOCUMENT;

	/**
	 * Specifies how the API handles inputs longer than the maximum token length.
	 */
	private CohereEmbeddingRequest.Truncate truncate = CohereEmbeddingRequest.Truncate.NONE;

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

	public static String getConfigPrefix() {
		return CONFIG_PREFIX;
	}

	public void setInputType(InputType inputType) {
		this.inputType = inputType;
	}

	public InputType getInputType() {
		return inputType;
	}

	public CohereEmbeddingRequest.Truncate getTruncate() {
		return truncate;
	}

	public void setTruncate(CohereEmbeddingRequest.Truncate truncate) {
		this.truncate = truncate;
	}

}
