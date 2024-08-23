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
package org.springframework.ai.openai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.embedding.EmbeddingOptions;

/**
 * @author Christian Tzolov
 * @since 0.8.0
 */
@JsonInclude(Include.NON_NULL)
public class OpenAiEmbeddingOptions implements EmbeddingOptions {

	// @formatter:off
	/**
	 * ID of the model to use.
	 */
	private @JsonProperty("model") String model;
	/**
	 * The format to return the embeddings in. Can be either float or base64.
	 */
	private @JsonProperty("encoding_format") String encodingFormat;
	/**
	 * The number of dimensions the resulting output embeddings should have. Only supported in text-embedding-3 and later models.
	 */
	private @JsonProperty("dimensions") Integer dimensions;
	/**
	 * A unique identifier representing your end-user, which can help OpenAI to monitor and detect abuse.
	 */
	private @JsonProperty("user") String user;
	// @formatter:on

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		protected OpenAiEmbeddingOptions options;

		public Builder() {
			this.options = new OpenAiEmbeddingOptions();
		}

		public Builder withModel(String model) {
			this.options.setModel(model);
			return this;
		}

		public Builder withEncodingFormat(String encodingFormat) {
			this.options.setEncodingFormat(encodingFormat);
			return this;
		}

		public Builder withDimensions(Integer dimensions) {
			this.options.dimensions = dimensions;
			return this;
		}

		public Builder withUser(String user) {
			this.options.setUser(user);
			return this;
		}

		public OpenAiEmbeddingOptions build() {
			return this.options;
		}

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
	public Integer getDimensions() {
		return this.dimensions;
	}

	public void setDimensions(Integer dimensions) {
		this.dimensions = dimensions;
	}

	public String getUser() {
		return this.user;
	}

	public void setUser(String user) {
		this.user = user;
	}

}
