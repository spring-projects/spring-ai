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

package org.springframework.ai.postgresml;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.postgresml.PostgresMlEmbeddingModel.VectorType;

/**
 * PostgresML Embedding Options.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @author Ilayaperumal Gopinathan
 * @author Sébastien Deleuze
 */
public class PostgresMlEmbeddingOptions implements EmbeddingOptions {

	public static final String DEFAULT_TRANSFORMER_MODEL = "distilbert-base-uncased";

	/**
	 * The Huggingface transformer model to use for the embedding.
	 */
	private final String transformer;

	/**
	 * PostgresML vector type to use for the embedding. Two options are supported:
	 * PG_ARRAY and PG_VECTOR.
	 */
	private final VectorType vectorType;

	/**
	 * Additional transformer specific options.
	 */
	private final @Nullable Map<String, Object> kwargs;

	/**
	 * The Document metadata aggregation mode.
	 */
	private final MetadataMode metadataMode;

	protected PostgresMlEmbeddingOptions(@Nullable String transformer, @Nullable VectorType vectorType,
			@Nullable Map<String, Object> kwargs, @Nullable MetadataMode metadataMode) {
		this.transformer = transformer != null ? transformer : DEFAULT_TRANSFORMER_MODEL;
		this.vectorType = vectorType != null ? vectorType : VectorType.PG_ARRAY;
		this.kwargs = kwargs != null ? Map.copyOf(kwargs) : null;
		this.metadataMode = metadataMode != null ? metadataMode : MetadataMode.EMBED;
	}

	public String getTransformer() {
		return this.transformer;
	}

	public VectorType getVectorType() {
		return this.vectorType;
	}

	public @Nullable Map<String, Object> getKwargs() {
		return this.kwargs;
	}

	public MetadataMode getMetadataMode() {
		return this.metadataMode;
	}

	@Override
	public @Nullable String getModel() {
		return null;
	}

	@Override
	public @Nullable Integer getDimensions() {
		return null;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private @Nullable String transformer;

		private @Nullable VectorType vectorType;

		private @Nullable Map<String, Object> kwargs;

		private @Nullable MetadataMode metadataMode;

		public Builder transformer(@Nullable String transformer) {
			this.transformer = transformer;
			return this;
		}

		public Builder vectorType(@Nullable VectorType vectorType) {
			this.vectorType = vectorType;
			return this;
		}

		public Builder kwargs(@Nullable Map<String, Object> kwargs) {
			if (kwargs != null) {
				this.kwargs = kwargs;
			}
			return this;
		}

		public Builder metadataMode(@Nullable MetadataMode metadataMode) {
			this.metadataMode = metadataMode;
			return this;
		}

		public PostgresMlEmbeddingOptions build() {
			return new PostgresMlEmbeddingOptions(this.transformer, this.vectorType, this.kwargs, this.metadataMode);
		}

	}

}
