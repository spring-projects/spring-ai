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

package org.springframework.ai.vertexai.embedding.text;

import org.springframework.ai.model.EmbeddingModelDescription;

/**
 * VertexAI Embedding Models: - <a href=
 * "https://cloud.google.com/vertex-ai/generative-ai/docs/model-reference/text-embeddings-api">Text
 * embeddings</a> - <a href=
 * "https://cloud.google.com/vertex-ai/generative-ai/docs/model-reference/multimodal-embeddings-api">Multimodal
 * embeddings</a>
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public enum VertexAiTextEmbeddingModelName implements EmbeddingModelDescription {

	/**
	 * English model. Expires on May 14, 2025.
	 */
	TEXT_EMBEDDING_004("text-embedding-004", "004", 768, "English text model"),

	/**
	 * Multilingual model. Expires on May 14, 2025.
	 */
	TEXT_MULTILINGUAL_EMBEDDING_002("text-multilingual-embedding-002", "002", 768, "Multilingual text model");

	private final String modelVersion;

	private final String modelName;

	private final String description;

	private final int dimensions;

	VertexAiTextEmbeddingModelName(String value, String modelVersion, int dimensions, String description) {
		this.modelName = value;
		this.modelVersion = modelVersion;
		this.dimensions = dimensions;
		this.description = description;
	}

	@Override
	public String getName() {
		return this.modelName;
	}

	@Override
	public String getVersion() {
		return this.modelVersion;
	}

	@Override
	public int getDimensions() {
		return this.dimensions;
	}

	@Override
	public String getDescription() {
		return this.description;
	}

}
