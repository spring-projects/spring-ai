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

	/**
	 * Client lever Ollama options. Use this property to configure generative temperature,
	 * topK and topP and alike parameters. The null values are ignored defaulting to the
	 * generative's defaults.
	 */
	private final Options options = new Options();

	public @Nullable String getModel() {
		return this.options.getModel();
	}

	public void setModel(@Nullable String model) {
		this.options.setModel(model);
	}

	public Options getOptions() {
		return this.options;
	}

	public static class Options {

		private @Nullable String model;

		private @Nullable Boolean truncate;

		private @Nullable String keepAlive;

		public @Nullable String getModel() {
			return this.model;
		}

		public void setModel(@Nullable String model) {
			this.model = model;
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
			OllamaEmbeddingOptions.Builder builder = OllamaEmbeddingOptions.builder();
			if (this.model != null) {
				builder.model(this.model);
			}
			if (this.truncate != null) {
				builder.truncate(this.truncate);
			}
			if (this.keepAlive != null) {
				builder.keepAlive(this.keepAlive);
			}
			return builder.build();
		}

	}

}
