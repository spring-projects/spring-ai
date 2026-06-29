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

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.voyageai.VoyageAiEmbeddingOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Voyage AI embedding model.
 *
 * @author Spring AI
 * @since 2.0.0
 */
@ConfigurationProperties(VoyageAiEmbeddingProperties.CONFIG_PREFIX)
public class VoyageAiEmbeddingProperties extends VoyageAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.voyageai.embedding";

	private MetadataMode metadataMode = MetadataMode.EMBED;

	private @Nullable String model;

	private @Nullable String inputType;

	private @Nullable Integer outputDimension;

	private @Nullable Boolean truncation;

	public VoyageAiEmbeddingProperties() {
		super.setBaseUrl(VoyageAiCommonProperties.DEFAULT_BASE_URL);
	}

	public MetadataMode getMetadataMode() {
		return this.metadataMode;
	}

	public void setMetadataMode(MetadataMode metadataMode) {
		this.metadataMode = metadataMode;
	}

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable String getInputType() {
		return this.inputType;
	}

	public void setInputType(@Nullable String inputType) {
		this.inputType = inputType;
	}

	public @Nullable Integer getOutputDimension() {
		return this.outputDimension;
	}

	public void setOutputDimension(@Nullable Integer outputDimension) {
		this.outputDimension = outputDimension;
	}

	public @Nullable Boolean getTruncation() {
		return this.truncation;
	}

	public void setTruncation(@Nullable Boolean truncation) {
		this.truncation = truncation;
	}

	public VoyageAiEmbeddingOptions toOptions() {
		return VoyageAiEmbeddingOptions.builder()
			.model(this.model)
			.inputType(this.inputType)
			.outputDimension(this.outputDimension)
			.truncation(this.truncation)
			.build();
	}

}
