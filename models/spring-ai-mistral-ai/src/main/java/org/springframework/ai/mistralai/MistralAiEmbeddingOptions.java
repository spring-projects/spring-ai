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

package org.springframework.ai.mistralai;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.mistralai.api.MistralAiApi;

/**
 * Options for the Mistral AI Embedding API.
 *
 * @author Ricken Bazolo
 * @author Thomas Vitale
 * @author Jason Smith
 * @author Sebastien Deleuze
 * @since 0.8.1
 */
public class MistralAiEmbeddingOptions implements EmbeddingOptions {

	public static final String DEFAULT_EMBEDDING_MODEL = MistralAiApi.EmbeddingModel.EMBED.getValue();

	public static final String DEFAULT_ENCODING_FORMAT = "float";

	/**
	 * ID of the model to use.
	 */
	private final String model;

	/**
	 * The format to return the embeddings in. Can be either float or base64.
	 */
	private final String encodingFormat;

	protected MistralAiEmbeddingOptions(@Nullable String model, @Nullable String encodingFormat) {
		this.model = (model != null ? model : DEFAULT_EMBEDDING_MODEL);
		this.encodingFormat = (encodingFormat != null ? encodingFormat : DEFAULT_ENCODING_FORMAT);
	}

	@Override
	public String getModel() {
		return this.model;
	}

	public String getEncodingFormat() {
		return this.encodingFormat;
	}

	public static MistralAiEmbeddingOptions.Builder builder() {
		return new Builder();
	}

	@Override
	public @Nullable Integer getDimensions() {
		return null;
	}

	public static final class Builder {

		private @Nullable String model;

		private @Nullable String encodingFormat;

		public Builder() {
		}

		public Builder model(String model) {
			this.model = model;
			return this;
		}

		public Builder encodingFormat(String encodingFormat) {
			this.encodingFormat = encodingFormat;
			return this;
		}

		public MistralAiEmbeddingOptions build() {
			return new MistralAiEmbeddingOptions(this.model, this.encodingFormat);
		}

	}

}
