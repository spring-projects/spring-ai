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

package org.springframework.ai.model.openai.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.OpenAiEmbeddingOptions.EncodingFormat;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

@ConfigurationProperties(OpenAiEmbeddingProperties.CONFIG_PREFIX)
public class OpenAiEmbeddingProperties extends AbstractOpenAiProperties {

	public static final String CONFIG_PREFIX = "spring.ai.openai.embedding";

	public static final String DEFAULT_EMBEDDING_MODEL = OpenAiEmbeddingOptions.DEFAULT_EMBEDDING_MODEL;

	private MetadataMode metadataMode = MetadataMode.EMBED;

	private @Nullable String model = DEFAULT_EMBEDDING_MODEL;

	private @Nullable String user;

	private @Nullable EncodingFormat encodingFormat;

	private @Nullable Integer dimensions;

	public MetadataMode getMetadataMode() {
		return this.metadataMode;
	}

	public void setMetadataMode(MetadataMode metadataMode) {
		this.metadataMode = metadataMode;
	}

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable String getUser() {
		return this.user;
	}

	public void setUser(@Nullable String user) {
		this.user = user;
	}

	public @Nullable EncodingFormat getEncodingFormat() {
		return this.encodingFormat;
	}

	public void setEncodingFormat(@Nullable EncodingFormat encodingFormat) {
		this.encodingFormat = encodingFormat;
	}

	public @Nullable Integer getDimensions() {
		return this.dimensions;
	}

	public void setDimensions(@Nullable Integer dimensions) {
		this.dimensions = dimensions;
	}

	public OpenAiEmbeddingOptions toOptions() {
		OpenAiEmbeddingOptions.Builder builder = OpenAiEmbeddingOptions.builder();
		if (this.getModel() != null) {
			builder.model(this.getModel());
		}
		if (this.user != null) {
			builder.user(this.user);
		}
		if (this.encodingFormat != null) {
			builder.encodingFormat(this.encodingFormat);
		}
		if (this.dimensions != null) {
			builder.dimensions(this.dimensions);
		}
		return builder.build();
	}

	private Options options = new Options();

	@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.embedding")
	@Deprecated(since = "2.0.0", forRemoval = true)
	public Options getOptions() {
		return this.options;
	}

	public void setOptions(Options options) {
		this.options = options;
	}

	public class Options {

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.embedding.model")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getModel() {
			return OpenAiEmbeddingProperties.this.getModel();
		}

		public void setModel(@Nullable String model) {
			OpenAiEmbeddingProperties.this.setModel(model);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.embedding.user")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getUser() {
			return OpenAiEmbeddingProperties.this.getUser();
		}

		public void setUser(@Nullable String user) {
			OpenAiEmbeddingProperties.this.setUser(user);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.embedding.encoding-format")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable EncodingFormat getEncodingFormat() {
			return OpenAiEmbeddingProperties.this.getEncodingFormat();
		}

		public void setEncodingFormat(@Nullable EncodingFormat encodingFormat) {
			OpenAiEmbeddingProperties.this.setEncodingFormat(encodingFormat);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.embedding.dimensions")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Integer getDimensions() {
			return OpenAiEmbeddingProperties.this.getDimensions();
		}

		public void setDimensions(@Nullable Integer dimensions) {
			OpenAiEmbeddingProperties.this.setDimensions(dimensions);
		}

	}

}
