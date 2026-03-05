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

package org.springframework.ai.mistralai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.embedding.EmbeddingOptions;

/**
 * Options for the Mistral AI Embedding API.
 *
 * @author Ricken Bazolo
 * @author Thomas Vitale
 * @author Jason Smith
 * @since 0.8.1
 */
@JsonInclude(Include.NON_NULL)
public class MistralAiEmbeddingOptions implements EmbeddingOptions {

	/**
	 * ID of the model to use.
	 */
	@SuppressWarnings("NullAway.Init")
	private @JsonProperty("model") String model;

	/**
	 * The format to return the embeddings in. Can be either float or base64.
	 */
	@SuppressWarnings("NullAway.Init")
	private @JsonProperty("encoding_format") String encodingFormat;

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

	public String getEncodingFormat() {
		return this.encodingFormat;
	}

	public void setEncodingFormat(String encodingFormat) {
		this.encodingFormat = encodingFormat;
	}

	@Override
	@JsonIgnore
	public @Nullable Integer getDimensions() {
		return null;
	}

	public static final class Builder {

		protected MistralAiEmbeddingOptions options;

		public Builder() {
			this.options = new MistralAiEmbeddingOptions();
		}

		public Builder withModel(String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder withEncodingFormat(String encodingFormat) {
			this.options.setEncodingFormat(encodingFormat);
			return this;
		}

		public MistralAiEmbeddingOptions build() {
			return this.options;
		}

	}

}
