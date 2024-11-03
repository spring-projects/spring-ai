/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.autoconfigure.postgresml;

import java.util.Map;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.postgresml.PostgresMlEmbeddingModel;
import org.springframework.ai.postgresml.PostgresMlEmbeddingOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.Assert;

/**
 * Configuration properties for Postgres ML.
 *
 * @author Utkarsh Srivastava
 * @author Christian Tzolov
 */
@ConfigurationProperties(PostgresMlEmbeddingProperties.CONFIG_PREFIX)
public class PostgresMlEmbeddingProperties {

	public static final String CONFIG_PREFIX = "spring.ai.postgresml.embedding";

	/**
	 * Enable Postgres ML embedding model.
	 */
	private boolean enabled = true;

	/**
	 * Create the extensions required for embedding
	 */
	private boolean createExtension;

	@NestedConfigurationProperty
	private PostgresMlEmbeddingOptions options = PostgresMlEmbeddingOptions.builder()
		.withTransformer(PostgresMlEmbeddingModel.DEFAULT_TRANSFORMER_MODEL)
		.withVectorType(PostgresMlEmbeddingModel.VectorType.PG_ARRAY)
		.withKwargs(Map.of())
		.withMetadataMode(MetadataMode.EMBED)
		.build();

	public PostgresMlEmbeddingOptions getOptions() {
		return this.options;
	}

	public void setOptions(PostgresMlEmbeddingOptions options) {
		Assert.notNull(options, "options must not be null.");
		Assert.notNull(options.getTransformer(), "transformer must not be null.");
		Assert.notNull(options.getVectorType(), "vectorType must not be null.");
		Assert.notNull(options.getKwargs(), "kwargs must not be null.");
		Assert.notNull(options.getMetadataMode(), "metadataMode must not be null.");

		this.options = options;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isCreateExtension() {
		return this.createExtension;
	}

	public void setCreateExtension(boolean createExtension) {
		this.createExtension = createExtension;
	}

}
