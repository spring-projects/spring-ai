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

package org.springframework.ai.model.vertexai.autoconfigure.embedding;

import org.springframework.ai.vertexai.embedding.text.VertexAiTextEmbeddingOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Vertex AI Gemini Chat.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
@ConfigurationProperties(VertexAiTextEmbeddingProperties.CONFIG_PREFIX)
public class VertexAiTextEmbeddingProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vertex.ai.embedding.text";

	private boolean enabled = true;

	/**
	 * Vertex AI Text Embedding API options.
	 */
	private VertexAiTextEmbeddingOptions options = VertexAiTextEmbeddingOptions.builder()
		.taskType(VertexAiTextEmbeddingOptions.TaskType.RETRIEVAL_DOCUMENT)
		.model(VertexAiTextEmbeddingOptions.DEFAULT_MODEL_NAME)
		.build();

	public VertexAiTextEmbeddingOptions getOptions() {
		return this.options;
	}

	public void setOptions(VertexAiTextEmbeddingOptions options) {
		this.options = options;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
