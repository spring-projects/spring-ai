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
package org.springframework.ai.postgresml;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.postgresml.PostgresMlEmbeddingModel.VectorType;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
@JsonInclude(Include.NON_NULL)
public class PostgresMlEmbeddingOptions implements EmbeddingOptions {

	// @formatter:off
	/**
	 * The Huggingface transformer model to use for the embedding.
	 */
	private @JsonProperty("transformer") String transformer = PostgresMlEmbeddingModel.DEFAULT_TRANSFORMER_MODEL;

	/**
	 * PostgresML vector type to use for the embedding.
	 * Two options are supported: PG_ARRAY and PG_VECTOR.
	 */
	private @JsonProperty("vectorType") VectorType vectorType = VectorType.PG_ARRAY;

	/**
	 * Additional transformer specific options.
	 */
	private @JsonProperty("kwargs") Map<String, Object> kwargs = Map.of();

	/**
	 * The Document metadata aggregation mode.
	 */
	private @JsonProperty("metadataMode") MetadataMode metadataMode = MetadataMode.EMBED;
	// @formatter:on

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		protected PostgresMlEmbeddingOptions options;

		public Builder() {
			this.options = new PostgresMlEmbeddingOptions();
		}

		public Builder withTransformer(String transformer) {
			this.options.setTransformer(transformer);
			return this;
		}

		public Builder withVectorType(VectorType vectorType) {
			this.options.setVectorType(vectorType);
			return this;
		}

		public Builder withKwargs(String kwargs) {
			this.options.setKwargs(ModelOptionsUtils.objectToMap(kwargs));
			return this;
		}

		public Builder withKwargs(Map<String, Object> kwargs) {
			this.options.setKwargs(kwargs);
			return this;
		}

		public Builder withMetadataMode(MetadataMode metadataMode) {
			this.options.setMetadataMode(metadataMode);
			return this;
		}

		public PostgresMlEmbeddingOptions build() {
			return this.options;
		}

	}

	public String getTransformer() {
		return this.transformer;
	}

	public void setTransformer(String transformer) {
		this.transformer = transformer;
	}

	public VectorType getVectorType() {
		return this.vectorType;
	}

	public void setVectorType(VectorType vectorType) {
		this.vectorType = vectorType;
	}

	public Map<String, Object> getKwargs() {
		return this.kwargs;
	}

	public void setKwargs(Map<String, Object> kwargs) {
		this.kwargs = kwargs;
	}

	public MetadataMode getMetadataMode() {
		return metadataMode;
	}

	public void setMetadataMode(MetadataMode metadataMode) {
		this.metadataMode = metadataMode;
	}

	@Override
	@JsonIgnore
	public String getModel() {
		return null;
	}

	@Override
	@JsonIgnore
	public Integer getDimensions() {
		return null;
	}

}
