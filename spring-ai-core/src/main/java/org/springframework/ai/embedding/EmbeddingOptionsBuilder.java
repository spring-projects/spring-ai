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
package org.springframework.ai.embedding;

/**
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class EmbeddingOptionsBuilder {

	private final DefaultEmbeddingOptions embeddingOptions = new DefaultEmbeddingOptions();

	private EmbeddingOptionsBuilder() {
	}

	public static EmbeddingOptionsBuilder builder() {
		return new EmbeddingOptionsBuilder();
	}

	public EmbeddingOptionsBuilder withModel(String model) {
		embeddingOptions.setModel(model);
		return this;
	}

	public EmbeddingOptionsBuilder withDimensions(Integer dimensions) {
		embeddingOptions.setDimensions(dimensions);
		return this;
	}

	public EmbeddingOptions build() {
		return embeddingOptions;
	}

	private static class DefaultEmbeddingOptions implements EmbeddingOptions {

		private String model;

		private Integer dimensions;

		@Override
		public String getModel() {
			return this.model;
		}

		public void setModel(String model) {
			this.model = model;
		}

		@Override
		public Integer getDimensions() {
			return this.dimensions;
		}

		public void setDimensions(Integer dimensions) {
			this.dimensions = dimensions;
		}

	}

}
