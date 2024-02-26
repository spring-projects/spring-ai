/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.autoconfigure.mistralai;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.mistralai.MistralAiEmbeddingOptions;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * @author Ricken Bazolo
 * @since 0.8.1
 */
@ConfigurationProperties(MistralAiEmbeddingProperties.CONFIG_PREFIX)
public class MistralAiEmbeddingProperties extends MistralAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.mistralai.embedding";

	public static final String DEFAULT_EMBEDDING_MODEL = MistralAiApi.EmbeddingModel.EMBED.getValue();

	public static final String DEFAULT_ENCODING_FORMAT = "float";

	/**
	 * Enable MistralAI embedding client.
	 */
	private boolean enabled = true;

	public MetadataMode metadataMode = MetadataMode.EMBED;

	@NestedConfigurationProperty
	private MistralAiEmbeddingOptions options = MistralAiEmbeddingOptions.builder()
		.withModel(DEFAULT_EMBEDDING_MODEL)
		.withEncodingFormat(DEFAULT_ENCODING_FORMAT)
		.build();

	public MistralAiEmbeddingProperties() {
		super.setBaseUrl(MistralAiCommonProperties.DEFAULT_BASE_URL);
	}

	public MistralAiEmbeddingOptions getOptions() {
		return this.options;
	}

	public void setOptions(MistralAiEmbeddingOptions options) {
		this.options = options;
	}

	public MetadataMode getMetadataMode() {
		return this.metadataMode;
	}

	public void setMetadataMode(MetadataMode metadataMode) {
		this.metadataMode = metadataMode;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
