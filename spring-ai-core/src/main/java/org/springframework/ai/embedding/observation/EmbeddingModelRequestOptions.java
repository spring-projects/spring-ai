/*
 * Copyright 2024 the original author or authors.
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
package org.springframework.ai.embedding.observation;

import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Represents client-side options for embedding model requests.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class EmbeddingModelRequestOptions implements EmbeddingOptions {

	private final String model;

	@Nullable
	private final Integer dimensions;

	@Nullable
	private final String encodingFormat;

	EmbeddingModelRequestOptions(Builder builder) {
		Assert.hasText(builder.model, "model cannot be null or empty");

		this.model = builder.model;
		this.dimensions = builder.dimensions;
		this.encodingFormat = builder.encodingFormat;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private String model;

		@Nullable
		private Integer dimensions;

		@Nullable
		private String encodingFormat;

		private Builder() {
		}

		public Builder model(String model) {
			this.model = model;
			return this;
		}

		public Builder dimensions(@Nullable Integer dimensions) {
			this.dimensions = dimensions;
			return this;
		}

		public Builder encodingFormat(@Nullable String encodingFormat) {
			this.encodingFormat = encodingFormat;
			return this;
		}

		public EmbeddingModelRequestOptions build() {
			return new EmbeddingModelRequestOptions(this);
		}

	}

	public String getModel() {
		return model;
	}

	@Nullable
	public Integer getDimensions() {
		return dimensions;
	}

	@Nullable
	public String getEncodingFormat() {
		return encodingFormat;
	}

}
