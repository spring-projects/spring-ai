/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.model.openaisdk.autoconfigure;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openaisdk.AbstractOpenAiSdkOptions;
import org.springframework.ai.openaisdk.OpenAiSdkEmbeddingOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(OpenAiSdkEmbeddingProperties.CONFIG_PREFIX)
public class OpenAiSdkEmbeddingProperties extends AbstractOpenAiSdkOptions {

	public static final String CONFIG_PREFIX = "spring.ai.openai-sdk.embedding";

	public static final String DEFAULT_EMBEDDING_MODEL = OpenAiSdkEmbeddingOptions.DEFAULT_EMBEDDING_MODEL;

	private MetadataMode metadataMode = MetadataMode.EMBED;

	@NestedConfigurationProperty
	private final OpenAiSdkEmbeddingOptions options = OpenAiSdkEmbeddingOptions.builder()
		.model(DEFAULT_EMBEDDING_MODEL)
		.build();

	public OpenAiSdkEmbeddingOptions getOptions() {
		return this.options;
	}

	public MetadataMode getMetadataMode() {
		return this.metadataMode;
	}

	public void setMetadataMode(MetadataMode metadataMode) {
		this.metadataMode = metadataMode;
	}

}
