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

package org.springframework.ai.model.bedrock.titan.autoconfigure;

import org.springframework.ai.bedrock.titan.BedrockTitanEmbeddingModel.InputType;
import org.springframework.ai.bedrock.titan.api.TitanEmbeddingBedrockApi.TitanEmbeddingModel;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bedrock Titan Embedding autoconfiguration properties.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
@ConfigurationProperties(BedrockTitanEmbeddingProperties.CONFIG_PREFIX)
public class BedrockTitanEmbeddingProperties {

	public static final String CONFIG_PREFIX = "spring.ai.bedrock.titan.embedding";

	/**
	 * Bedrock Titan Embedding generative name. Defaults to 'amazon.titan-embed-image-v1'.
	 */
	private String model = TitanEmbeddingModel.TITAN_EMBED_IMAGE_V1.id();

	/**
	 * Titan Embedding API input types. Could be either text or image (encoded in base64).
	 * Defaults to {@link InputType#IMAGE}.
	 */
	private InputType inputType = InputType.IMAGE;

	public static String getConfigPrefix() {
		return CONFIG_PREFIX;
	}

	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public InputType getInputType() {
		return this.inputType;
	}

	public void setInputType(InputType inputType) {
		this.inputType = inputType;
	}

}
