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

package org.springframework.ai.autoconfigure.ollama;

import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Ollama Embedding autoconfiguration properties.
 *
 * @author Christian Tzolov
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
	@NestedConfigurationProperty
	private OllamaOptions options = OllamaOptions.create().withModel(OllamaOptions.DEFAULT_MODEL);

	public String getModel() {
		return this.options.getModel();
	}

	public void setModel(String model) {
		this.options.setModel(model);
	}

	public OllamaOptions getOptions() {
		return options;
	}

}
