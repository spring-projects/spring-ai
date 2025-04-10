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

package org.springframework.ai.model.qianfan.autoconfigure;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.qianfan.QianFanEmbeddingOptions;
import org.springframework.ai.qianfan.api.QianFanApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for QianFan embedding model.
 *
 * @author Geng Rong
 */
@ConfigurationProperties(QianFanEmbeddingProperties.CONFIG_PREFIX)
public class QianFanEmbeddingProperties extends QianFanParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.qianfan.embedding";

	private MetadataMode metadataMode = MetadataMode.EMBED;

	@NestedConfigurationProperty
	private QianFanEmbeddingOptions options = QianFanEmbeddingOptions.builder()
		.model(QianFanApi.DEFAULT_EMBEDDING_MODEL)
		.build();

	public QianFanEmbeddingOptions getOptions() {
		return this.options;
	}

	public void setOptions(QianFanEmbeddingOptions options) {
		this.options = options;
	}

	public MetadataMode getMetadataMode() {
		return this.metadataMode;
	}

	public void setMetadataMode(MetadataMode metadataMode) {
		this.metadataMode = metadataMode;
	}

}
