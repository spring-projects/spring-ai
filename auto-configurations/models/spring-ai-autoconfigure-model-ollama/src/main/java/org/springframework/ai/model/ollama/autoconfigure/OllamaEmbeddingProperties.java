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

package org.springframework.ai.model.ollama.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

/**
 * Ollama Embedding autoconfiguration properties.
 *
 * @author Christian Tzolov
 * @author Sebastien Deleuze
 * @since 0.8.0
 */
@ConfigurationProperties(OllamaEmbeddingProperties.CONFIG_PREFIX)
public class OllamaEmbeddingProperties {

	public static final String CONFIG_PREFIX = "spring.ai.ollama.embedding";

	private @Nullable String model;

	private @Nullable Integer dimensions;

	private @Nullable Boolean truncate;

	private @Nullable String keepAlive;

	private Options options = new Options();

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable Integer getDimensions() {
		return this.dimensions;
	}

	public void setDimensions(@Nullable Integer dimensions) {
		this.dimensions = dimensions;
	}

	public @Nullable Boolean getTruncate() {
		return this.truncate;
	}

	public void setTruncate(@Nullable Boolean truncate) {
		this.truncate = truncate;
	}

	public @Nullable String getKeepAlive() {
		return this.keepAlive;
	}

	public void setKeepAlive(@Nullable String keepAlive) {
		this.keepAlive = keepAlive;
	}

	public OllamaEmbeddingOptions toOptions() {
		return OllamaEmbeddingOptions.builder()
			.model(this.model)
			.truncate(this.truncate)
			.keepAlive(this.keepAlive)
			.dimensions(this.dimensions)
			.build();
	}

	@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.embedding")
	@Deprecated(since = "2.0.0", forRemoval = true)
	public Options getOptions() {
		return this.options;
	}

	public void setOptions(Options options) {
		this.options = options;
	}

	public class Options {

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.embedding.model")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getModel() {
			return OllamaEmbeddingProperties.this.getModel();
		}

		public void setModel(@Nullable String model) {
			OllamaEmbeddingProperties.this.setModel(model);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.embedding.truncate")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Boolean getTruncate() {
			return OllamaEmbeddingProperties.this.getTruncate();
		}

		public void setTruncate(@Nullable Boolean truncate) {
			OllamaEmbeddingProperties.this.setTruncate(truncate);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.ollama.embedding.keep-alive")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getKeepAlive() {
			return OllamaEmbeddingProperties.this.getKeepAlive();
		}

		public void setKeepAlive(@Nullable String keepAlive) {
			OllamaEmbeddingProperties.this.setKeepAlive(keepAlive);
		}

	}

}
