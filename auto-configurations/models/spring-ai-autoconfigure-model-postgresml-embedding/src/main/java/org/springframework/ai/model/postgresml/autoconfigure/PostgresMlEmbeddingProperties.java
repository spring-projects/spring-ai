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

package org.springframework.ai.model.postgresml.autoconfigure;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.postgresml.PostgresMlEmbeddingModel;
import org.springframework.ai.postgresml.PostgresMlEmbeddingModel.VectorType;
import org.springframework.ai.postgresml.PostgresMlEmbeddingOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

/**
 * Configuration properties for Postgres ML.
 *
 * @author Utkarsh Srivastava
 * @author Christian Tzolov
 * @author Sebastien Deleuze
 */
@ConfigurationProperties(PostgresMlEmbeddingProperties.CONFIG_PREFIX)
public class PostgresMlEmbeddingProperties {

	public static final String CONFIG_PREFIX = "spring.ai.postgresml.embedding";

	/**
	 * Create the extensions required for embedding
	 */
	private boolean createExtension;

	private @Nullable String transformer = PostgresMlEmbeddingModel.DEFAULT_TRANSFORMER_MODEL;

	private @Nullable VectorType vectorType = VectorType.PG_ARRAY;

	private @Nullable Map<String, Object> kwargs = Map.of();

	private @Nullable MetadataMode metadataMode = MetadataMode.EMBED;

	public boolean isCreateExtension() {
		return this.createExtension;
	}

	public void setCreateExtension(boolean createExtension) {
		this.createExtension = createExtension;
	}

	public @Nullable String getTransformer() {
		return this.transformer;
	}

	public void setTransformer(@Nullable String transformer) {
		this.transformer = transformer;
	}

	public @Nullable VectorType getVectorType() {
		return this.vectorType;
	}

	public void setVectorType(@Nullable VectorType vectorType) {
		this.vectorType = vectorType;
	}

	public @Nullable Map<String, Object> getKwargs() {
		return this.kwargs;
	}

	public void setKwargs(@Nullable Map<String, Object> kwargs) {
		this.kwargs = kwargs;
	}

	public @Nullable MetadataMode getMetadataMode() {
		return this.metadataMode;
	}

	public void setMetadataMode(@Nullable MetadataMode metadataMode) {
		this.metadataMode = metadataMode;
	}

	public PostgresMlEmbeddingOptions toOptions() {
		PostgresMlEmbeddingOptions.Builder builder = PostgresMlEmbeddingOptions.builder();
		if (this.transformer != null) {
			builder.transformer(this.transformer);
		}
		if (this.vectorType != null) {
			builder.vectorType(this.vectorType);
		}
		if (this.kwargs != null) {
			builder.kwargs(this.kwargs);
		}
		if (this.metadataMode != null) {
			builder.metadataMode(this.metadataMode);
		}
		return builder.build();
	}

	private Options options = new Options();

	@DeprecatedConfigurationProperty(replacement = "spring.ai.postgresml.embedding")
	@Deprecated(since = "2.0.0", forRemoval = true)
	public Options getOptions() {
		return this.options;
	}

	public void setOptions(Options options) {
		this.options = options;
	}

	public class Options {

		@DeprecatedConfigurationProperty(replacement = "spring.ai.postgresml.embedding.transformer")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getTransformer() {
			return PostgresMlEmbeddingProperties.this.getTransformer();
		}

		public void setTransformer(@Nullable String transformer) {
			PostgresMlEmbeddingProperties.this.setTransformer(transformer);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.postgresml.embedding.vector-type")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable VectorType getVectorType() {
			return PostgresMlEmbeddingProperties.this.getVectorType();
		}

		public void setVectorType(@Nullable VectorType vectorType) {
			PostgresMlEmbeddingProperties.this.setVectorType(vectorType);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.postgresml.embedding.kwargs")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable Map<String, Object> getKwargs() {
			return PostgresMlEmbeddingProperties.this.getKwargs();
		}

		public void setKwargs(@Nullable Map<String, Object> kwargs) {
			PostgresMlEmbeddingProperties.this.setKwargs(kwargs);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.postgresml.embedding.metadata-mode")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable MetadataMode getMetadataMode() {
			return PostgresMlEmbeddingProperties.this.getMetadataMode();
		}

		public void setMetadataMode(@Nullable MetadataMode metadataMode) {
			PostgresMlEmbeddingProperties.this.setMetadataMode(metadataMode);
		}

	}

}
