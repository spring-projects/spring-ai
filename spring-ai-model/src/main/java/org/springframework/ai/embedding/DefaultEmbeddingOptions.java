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

import org.jspecify.annotations.Nullable;

/**
 * Default implementation of {@link EmbeddingOptions}.
 *
 * @author Thomas Vitale
 */
public class DefaultEmbeddingOptions implements EmbeddingOptions {

	private @Nullable String model;

	private @Nullable Integer dimensions;

	@Override
	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	public @Nullable Integer getDimensions() {
		return this.dimensions;
	}

	public void setDimensions(Integer dimensions) {
		this.dimensions = dimensions;
	}

}
