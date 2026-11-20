/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.model.voyageai.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.voyageai.VoyageAiContextualizedEmbeddingOptions;
import org.springframework.ai.voyageai.api.VoyageAiApi;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties of the Voyage AI contextualized embedding model. The model bean is created
 * when {@code spring.ai.voyageai.contextualized-embedding.model} is set.
 *
 * @author Zoltan Fodi
 * @since 2.1.1
 */
@ConfigurationProperties(VoyageAiContextualizedEmbeddingProperties.CONFIG_PREFIX)
public class VoyageAiContextualizedEmbeddingProperties {

	public static final String CONFIG_PREFIX = "spring.ai.voyageai.contextualized-embedding";

	/**
	 * The contextualized embedding model: {@code voyage-context-4} or
	 * {@code voyage-context-3}. Required, there is no default.
	 */
	private @Nullable String model;

	/**
	 * The dimension of the vectors: 256, 512, 1024 or 2048. Left unset, the model returns
	 * 1024.
	 */
	private @Nullable Integer dimensions;

	/**
	 * Whether the inputs are documents or queries. A request can also set it.
	 */
	private VoyageAiApi.@Nullable InputType inputType;

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable Integer getDimensions() {
		return this.dimensions;
	}

	public void setDimensions(@Nullable Integer dimensions) {
		this.dimensions = dimensions;
	}

	public VoyageAiApi.@Nullable InputType getInputType() {
		return this.inputType;
	}

	public void setInputType(VoyageAiApi.@Nullable InputType inputType) {
		this.inputType = inputType;
	}

	public VoyageAiContextualizedEmbeddingOptions toOptions() {
		return VoyageAiContextualizedEmbeddingOptions.builder()
			.model(this.model)
			.dimensions(this.dimensions)
			.inputType(this.inputType)
			.build();
	}

}
