/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.model.huggingface.autoconfigure;

import org.springframework.ai.huggingface.HuggingfaceEmbeddingOptions;
import org.springframework.ai.huggingface.api.HuggingfaceApi;
import org.springframework.ai.huggingface.api.common.HuggingfaceApiConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * HuggingFace Embedding autoconfiguration properties.
 *
 * @author Myeongdeok Kang
 */
@ConfigurationProperties(HuggingfaceEmbeddingProperties.CONFIG_PREFIX)
public class HuggingfaceEmbeddingProperties {

	public static final String CONFIG_PREFIX = "spring.ai.huggingface.embedding";

	/**
	 * Enable HuggingFace embedding model autoconfiguration.
	 */
	private boolean enabled = true;

	/**
	 * Base URL for the HuggingFace Embedding API (Feature Extraction endpoint).
	 */
	private String url = HuggingfaceApiConstants.DEFAULT_EMBEDDING_BASE_URL;

	/**
	 * Client-level HuggingFace embedding options. Use this property to configure the
	 * model, dimensions, and other parameters. Null values are ignored, defaulting to the
	 * API defaults.
	 */
	@NestedConfigurationProperty
	private final HuggingfaceEmbeddingOptions options = HuggingfaceEmbeddingOptions.builder()
		.model(HuggingfaceApi.DEFAULT_EMBEDDING_MODEL)
		.build();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getUrl() {
		return this.url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getModel() {
		return this.options.getModel();
	}

	public void setModel(String model) {
		this.options.setModel(model);
	}

	public HuggingfaceEmbeddingOptions getOptions() {
		return this.options;
	}

}
