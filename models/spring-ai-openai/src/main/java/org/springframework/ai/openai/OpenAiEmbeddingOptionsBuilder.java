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

/**
 * @author youngmon
 * @version 0.8.1
 */
public class OpenAiEmbeddingOptionsBuilder {

	private String model;

	private String encodingFormat;

	private String user;

	private OpenAiEmbeddingOptionsBuilder() {
	}

	public static OpenAiEmbeddingOptionsBuilder builder() {
		return new OpenAiEmbeddingOptionsBuilder();
	}

	/**
	 * Copy Constructor for {@link OpenAiEmbeddingOptionsBuilder}
	 * @param options Existing {@link OpenAiEmbeddingOptions}
	 * @return new OpenAiEmbeddingOptionsBuilder
	 */
	public static OpenAiEmbeddingOptionsBuilder builder(OpenAiEmbeddingOptions options) {
		return builder().withUser(options.getUser())
			.withModel(options.getModel())
			.withEncodingFormat(options.getEncodingFormat());
	}

	public OpenAiEmbeddingOptionsBuilder withUser(final String user) {
		if (user == null)
			return this;
		this.user = user;
		return this;
	}

	public OpenAiEmbeddingOptionsBuilder withModel(final String model) {
		if (model == null)
			return this;
		this.model = model;
		return this;
	}

	public OpenAiEmbeddingOptionsBuilder withEncodingFormat(final String encodingFormat) {
		if (encodingFormat == null)
			return this;
		this.encodingFormat = encodingFormat;
		return this;
	}

	public OpenAiEmbeddingOptions build() {
		return new OpenAiEmbeddingOptionsImpl(this);
	}

	private static class OpenAiEmbeddingOptionsImpl extends OpenAiEmbeddingOptions {

		private final String model;

		private final String encodingFormat;

		private final String user;

		private OpenAiEmbeddingOptionsImpl(final OpenAiEmbeddingOptionsBuilder builder) {
			this.user = builder.user;
			this.encodingFormat = builder.encodingFormat;
			this.model = builder.model;
		}

		@Override
		public String getModel() {
			return this.model;
		}

		@Override
		public String getEncodingFormat() {
			return this.encodingFormat;
		}

		@Override
		public String getUser() {
			return this.user;
		}

	}

}
