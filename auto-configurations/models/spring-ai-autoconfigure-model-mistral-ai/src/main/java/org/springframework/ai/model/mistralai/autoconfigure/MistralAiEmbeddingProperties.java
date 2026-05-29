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

package org.springframework.ai.model.mistralai.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.mistralai.MistralAiEmbeddingOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for MistralAI embedding model.
 *
 * @author Ricken Bazolo
 * @author Sebastien Deleuze
 * @since 0.8.1
 */
@ConfigurationProperties(MistralAiEmbeddingProperties.CONFIG_PREFIX)
public class MistralAiEmbeddingProperties extends MistralAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.mistralai.embedding";

	public MetadataMode metadataMode = MetadataMode.EMBED;

	private final Options options = new Options();

	public MistralAiEmbeddingProperties() {
		super.setBaseUrl(MistralAiCommonProperties.DEFAULT_BASE_URL);
	}

	public Options getOptions() {
		return this.options;
	}

	public MetadataMode getMetadataMode() {
		return this.metadataMode;
	}

	public void setMetadataMode(MetadataMode metadataMode) {
		this.metadataMode = metadataMode;
	}

	public static class Options {

		private @Nullable String model;

		private @Nullable String encodingFormat;

		public @Nullable String getModel() {
			return this.model;
		}

		public void setModel(@Nullable String model) {
			this.model = model;
		}

		public @Nullable String getEncodingFormat() {
			return this.encodingFormat;
		}

		public void setEncodingFormat(@Nullable String encodingFormat) {
			this.encodingFormat = encodingFormat;
		}

		public MistralAiEmbeddingOptions toOptions() {
			return MistralAiEmbeddingOptions.builder().model(this.model).encodingFormat(this.encodingFormat).build();
		}

	}

}
