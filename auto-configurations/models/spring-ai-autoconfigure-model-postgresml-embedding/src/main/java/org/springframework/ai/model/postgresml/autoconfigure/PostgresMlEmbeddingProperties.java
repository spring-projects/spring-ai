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

package org.springframework.ai.model.postgresml.autoconfigure;

import java.util.Map;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.postgresml.PostgresMlEmbeddingModel;
import org.springframework.ai.postgresml.PostgresMlEmbeddingOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

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
	 * Create the extensions required for embedding
	 */
	private boolean createExtension;

	@NestedConfigurationProperty
	private final PostgresMlEmbeddingOptions options = PostgresMlEmbeddingOptions.builder()
		.transformer(PostgresMlEmbeddingModel.DEFAULT_TRANSFORMER_MODEL)
		.vectorType(PostgresMlEmbeddingModel.VectorType.PG_ARRAY)
		.kwargs(Map.of())
		.metadataMode(MetadataMode.EMBED)
		.build();

	public PostgresMlEmbeddingOptions getOptions() {
		return this.options;
	}

	public boolean isCreateExtension() {
		return this.createExtension;
	}

	public void setCreateExtension(boolean createExtension) {
		this.createExtension = createExtension;
	}

}
