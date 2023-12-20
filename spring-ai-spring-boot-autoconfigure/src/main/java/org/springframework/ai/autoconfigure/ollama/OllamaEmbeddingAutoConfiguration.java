/*
 * Copyright 2023-2023 the original author or authors.
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

import org.springframework.ai.autoconfigure.NativeHints;
import org.springframework.ai.ollama.OllamaEmbeddingClient;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * {@link AutoConfiguration Auto-configuration} for Ollama Embedding Client.
 *
 * @author Christian Tzolov
 * @since 0.8.0
 */
@AutoConfiguration
@ConditionalOnClass(OllamaApi.class)
@EnableConfigurationProperties({ OllamaEmbeddingProperties.class })
@ConditionalOnProperty(prefix = OllamaEmbeddingProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
		matchIfMissing = true)
@ImportRuntimeHints(NativeHints.class)
public class OllamaEmbeddingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public OllamaApi ollamaApi(OllamaEmbeddingProperties properties) {
		return new OllamaApi(properties.getBaseUrl());
	}

	@Bean
	@ConditionalOnMissingBean
	public OllamaEmbeddingClient ollamaEmbeddingClient(OllamaApi ollamaApi, OllamaEmbeddingProperties properties) {

		return new OllamaEmbeddingClient(ollamaApi).withModel(properties.getModel())
			.withOptions(properties.getOptions());
	}

}
