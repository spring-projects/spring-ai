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

package org.springframework.ai.voyageai;

import org.springframework.ai.model.EmbeddingModelDescription;

/**
 * Voyage AI text embedding models.
 *
 * @see <a href="https://docs.voyageai.com/docs/embeddings">Voyage AI Text Embeddings</a>
 * @author Spring AI
 * @since 2.0.0
 */
public enum VoyageAiEmbeddingModelName implements EmbeddingModelDescription {

	/**
	 * General-purpose, multilingual embedding model. Recommended default.
	 */
	VOYAGE_3_5("voyage-3.5", 1024, "General-purpose multilingual model"),

	/**
	 * Lightweight, lower-latency general-purpose model.
	 */
	VOYAGE_3_5_LITE("voyage-3.5-lite", 1024, "Lightweight general-purpose model"),

	/**
	 * Highest-quality general-purpose model.
	 */
	VOYAGE_3_LARGE("voyage-3-large", 1024, "Highest-quality general-purpose model"),

	/**
	 * Model optimized for code retrieval.
	 */
	VOYAGE_CODE_3("voyage-code-3", 1024, "Code-optimized model"),

	/**
	 * Model optimized for finance domain retrieval.
	 */
	VOYAGE_FINANCE_2("voyage-finance-2", 1024, "Finance-optimized model"),

	/**
	 * Model optimized for legal domain retrieval.
	 */
	VOYAGE_LAW_2("voyage-law-2", 1024, "Legal-optimized model");

	private final String modelName;

	private final int dimensions;

	private final String description;

	VoyageAiEmbeddingModelName(String modelName, int dimensions, String description) {
		this.modelName = modelName;
		this.dimensions = dimensions;
		this.description = description;
	}

	@Override
	public String getName() {
		return this.modelName;
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
