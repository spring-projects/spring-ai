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
package org.springframework.ai.autoconfigure.zhipuai;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.zhipuai.ZhiPuAiEmbeddingOptions;
import org.springframework.ai.zhipuai.api.ZhiPuAiApi;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * @author Geng Rong
 */
@ConfigurationProperties(ZhiPuAiEmbeddingProperties.CONFIG_PREFIX)
public class ZhiPuAiEmbeddingProperties extends ZhiPuAiParentProperties {

	public static final String CONFIG_PREFIX = "spring.ai.zhipuai.embedding";

	public static final String DEFAULT_EMBEDDING_MODEL = ZhiPuAiApi.EmbeddingModel.Embedding_2.value;

	/**
	 * Enable ZhiPuAI embedding model.
	 */
	private boolean enabled = true;

	private MetadataMode metadataMode = MetadataMode.EMBED;

	@NestedConfigurationProperty
	private ZhiPuAiEmbeddingOptions options = ZhiPuAiEmbeddingOptions.builder()
		.withModel(DEFAULT_EMBEDDING_MODEL)
		.build();

	public ZhiPuAiEmbeddingOptions getOptions() {
		return this.options;
	}

	public void setOptions(ZhiPuAiEmbeddingOptions options) {
		this.options = options;
	}

	public MetadataMode getMetadataMode() {
		return this.metadataMode;
	}

	public void setMetadataMode(MetadataMode metadataMode) {
		this.metadataMode = metadataMode;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

}
