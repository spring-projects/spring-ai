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

package org.springframework.ai.autoconfigure.vertexai.embedding;

import org.springframework.ai.vertexai.embedding.multimodal.VertexAiMultimodalEmbeddingOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Vertex AI Gemini Chat.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
@ConfigurationProperties(VertexAiMultimodalEmbeddingProperties.CONFIG_PREFIX)
public class VertexAiMultimodalEmbeddingProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vertex.ai.embedding.multimodal";

	private boolean enabled = true;

	/**
	 * Vertex AI Text Embedding API options.
	 */
	private VertexAiMultimodalEmbeddingOptions options = VertexAiMultimodalEmbeddingOptions.builder()
		.model(VertexAiMultimodalEmbeddingOptions.DEFAULT_MODEL_NAME)
		.build();

	public VertexAiMultimodalEmbeddingOptions getOptions() {
		return this.options;
	}

	public void setOptions(VertexAiMultimodalEmbeddingOptions options) {
		this.options = options;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
