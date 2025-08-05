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

package org.springframework.ai.model.google.genai.autoconfigure.embedding;

import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModelName;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for Google GenAI Text Embedding.
 *
 * @author Christian Tzolov
 * @author Mark Pollack
 * @author Ilayaperumal Gopinathan
 * @since 1.1.0
 */
@ConfigurationProperties(GoogleGenAiTextEmbeddingProperties.CONFIG_PREFIX)
public class GoogleGenAiTextEmbeddingProperties {

	public static final String CONFIG_PREFIX = "spring.ai.google.genai.embedding.text";

	public static final String DEFAULT_MODEL = GoogleGenAiTextEmbeddingModelName.TEXT_EMBEDDING_004.getName();

	/**
	 * Google GenAI Text Embedding API options.
	 */
	@NestedConfigurationProperty
	private GoogleGenAiTextEmbeddingOptions options = GoogleGenAiTextEmbeddingOptions.builder()
		.model(DEFAULT_MODEL)
		.build();

	public GoogleGenAiTextEmbeddingOptions getOptions() {
		return this.options;
	}

	public void setOptions(GoogleGenAiTextEmbeddingOptions options) {
		this.options = options;
	}

}
