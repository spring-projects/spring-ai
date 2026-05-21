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

package org.springframework.ai.minimax;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.embedding.EmbeddingOptions;

/**
 * This class represents the options for MiniMax embedding.
 *
 * @author Geng Rong
 * @author Thomas Vitale
 * @since 1.0.0 M1
 */
public class MiniMaxEmbeddingOptions implements EmbeddingOptions {

	// @formatter:off
	/**
	 * ID of the model to use.
	 */
	@SuppressWarnings("NullAway.Init")
	private String model;
	// @formatter:on

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	@Override
	public @Nullable Integer getDimensions() {
		return null;
	}

	public static final class Builder {

		protected MiniMaxEmbeddingOptions options;

		public Builder() {
			this.options = new MiniMaxEmbeddingOptions();
		}

		public Builder model(String model) {
			this.options.setModel(model);
			return this;
		}

		public MiniMaxEmbeddingOptions build() {
			return this.options;
		}

	}

}
