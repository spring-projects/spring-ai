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

package org.springframework.ai.model.mistralai.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.mistralai.MistralAiEmbeddingOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

/**
 * Configuration properties for MistralAI embedding model.
 *
 * @author Ricken Bazolo
 * @author Sebastien Deleuze
 * @since 0.8.1
 */
@ConfigurationProperties(MistralAiEmbeddingProperties.CONFIG_PREFIX)
public class MistralAiEmbeddingProperties extends MistralAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.mistralai.embedding";

	public MetadataMode metadataMode = MetadataMode.EMBED;

	public MistralAiEmbeddingProperties() {
		super.setBaseUrl(MistralAiCommonProperties.DEFAULT_BASE_URL);
	}

	public MetadataMode getMetadataMode() {
		return this.metadataMode;
	}

	public void setMetadataMode(MetadataMode metadataMode) {
		this.metadataMode = metadataMode;
	}

	private @Nullable String model;

	private @Nullable String encodingFormat;

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable String getEncodingFormat() {
		return this.encodingFormat;
	}

	public void setEncodingFormat(@Nullable String encodingFormat) {
		this.encodingFormat = encodingFormat;
	}

	public MistralAiEmbeddingOptions toOptions() {
		return MistralAiEmbeddingOptions.builder().model(this.model).encodingFormat(this.encodingFormat).build();
	}

	private Options options = new Options();

	@DeprecatedConfigurationProperty(replacement = "spring.ai.mistralai.embedding")
	@Deprecated(since = "2.0.0", forRemoval = true)
	public Options getOptions() {
		return this.options;
	}

	public void setOptions(Options options) {
		this.options = options;
	}

	public class Options {

		@DeprecatedConfigurationProperty(replacement = "spring.ai.mistralai.embedding.model")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getModel() {
			return MistralAiEmbeddingProperties.this.getModel();
		}

		public void setModel(@Nullable String model) {
			MistralAiEmbeddingProperties.this.setModel(model);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.mistralai.embedding.encoding-format")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getEncodingFormat() {
			return MistralAiEmbeddingProperties.this.getEncodingFormat();
		}

		public void setEncodingFormat(@Nullable String encodingFormat) {
			MistralAiEmbeddingProperties.this.setEncodingFormat(encodingFormat);
		}

	}

}
