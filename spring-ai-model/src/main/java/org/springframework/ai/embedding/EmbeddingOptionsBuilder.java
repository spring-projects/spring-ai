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

package org.springframework.ai.embedding;

/**
 * Builder for {@link EmbeddingOptions}.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 * @deprecated in favor of {@link EmbeddingOptions#builder()}
 */
@Deprecated
public final class EmbeddingOptionsBuilder implements EmbeddingOptions.Builder {

	private final DefaultEmbeddingOptions embeddingOptions = new DefaultEmbeddingOptions();

	private EmbeddingOptionsBuilder() {
	}

	public static EmbeddingOptionsBuilder builder() {
		return new EmbeddingOptionsBuilder();
	}

	public EmbeddingOptionsBuilder model(String model) {
		this.embeddingOptions.setModel(model);
		return this;
	}

	@Deprecated
	public EmbeddingOptionsBuilder withModel(String model) {
		return model(model);
	}

	public EmbeddingOptionsBuilder dimensions(Integer dimensions) {
		this.embeddingOptions.setDimensions(dimensions);
		return this;
	}

	@Deprecated
	public EmbeddingOptionsBuilder withDimensions(Integer dimensions) {
		return dimensions(dimensions);
	}

	public EmbeddingOptions build() {
		return this.embeddingOptions;
	}

}
