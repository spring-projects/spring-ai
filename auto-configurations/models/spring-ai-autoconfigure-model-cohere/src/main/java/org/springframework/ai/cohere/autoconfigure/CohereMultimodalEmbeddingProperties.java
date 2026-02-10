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

package org.springframework.ai.cohere.autoconfigure;

import java.util.List;

import org.springframework.ai.cohere.api.CohereApi.EmbeddingModel;
import org.springframework.ai.cohere.api.CohereApi.EmbeddingType;
import org.springframework.ai.cohere.embedding.CohereMultimodalEmbeddingOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for Cohere multimodal embedding model.
 *
 * @author Ricken Bazolo
 */
@ConfigurationProperties(CohereMultimodalEmbeddingProperties.CONFIG_PREFIX)
public class CohereMultimodalEmbeddingProperties extends CohereParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.cohere.embedding.multimodal";

	public static final String DEFAULT_EMBEDDING_MODEL = EmbeddingModel.EMBED_V4.getValue();

	public static final String DEFAULT_ENCODING_FORMAT = EmbeddingType.FLOAT.name();

	@NestedConfigurationProperty
	private final CohereMultimodalEmbeddingOptions options = CohereMultimodalEmbeddingOptions.builder()
		.model(DEFAULT_EMBEDDING_MODEL)
		.embeddingTypes(List.of(EmbeddingType.valueOf(DEFAULT_ENCODING_FORMAT)))
		.build();

	public CohereMultimodalEmbeddingProperties() {
		super.setBaseUrl(CohereCommonProperties.DEFAULT_BASE_URL);
	}

	public CohereMultimodalEmbeddingOptions getOptions() {
		return this.options;
	}

}
