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

package org.springframework.ai.embedding;

import org.jspecify.annotations.Nullable;

/**
 * Default implementation of {@link EmbeddingOptions}.
 *
 * @author Thomas Vitale
 * @author Sebastien Deleuze
 */
public class DefaultEmbeddingOptions implements EmbeddingOptions {

	private final @Nullable String model;

	private final @Nullable Integer dimensions;

	protected DefaultEmbeddingOptions(@Nullable String model, @Nullable Integer dimensions) {
		this.model = model;
		this.dimensions = dimensions;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public @Nullable String getModel() {
		return this.model;
	}

	@Override
	public @Nullable Integer getDimensions() {
		return this.dimensions;
	}

	public static final class Builder implements EmbeddingOptions.Builder {

		private @Nullable String model;

		private @Nullable Integer dimensions;

		private Builder() {
		}

		public Builder model(@Nullable String model) {
			this.model = model;
			return this;
		}

		public Builder dimensions(@Nullable Integer dimensions) {
			this.dimensions = dimensions;
			return this;
		}

		public DefaultEmbeddingOptions build() {
			return new DefaultEmbeddingOptions(this.model, this.dimensions);
		}

	}

}
