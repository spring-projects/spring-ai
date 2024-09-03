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
package org.springframework.ai.autoconfigure.vertexai.palm2;

import org.springframework.ai.autoconfigure.retry.SpringAiRetryAutoConfiguration;
import org.springframework.ai.vertexai.palm2.VertexAiPaLm2ChatModel;
import org.springframework.ai.vertexai.palm2.VertexAiPaLm2EmbeddingModel;
import org.springframework.ai.vertexai.palm2.api.VertexAiPaLm2Api;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@AutoConfiguration(after = RestClientAutoConfiguration.class)
@ConditionalOnClass(VertexAiPaLm2Api.class)
@EnableConfigurationProperties({ VertexAiPalm2ConnectionProperties.class, VertexAiPlam2ChatProperties.class,
		VertexAiPalm2EmbeddingProperties.class })
@ImportAutoConfiguration(classes = { SpringAiRetryAutoConfiguration.class, RestClientAutoConfiguration.class,
		WebClientAutoConfiguration.class })
public class VertexAiPalm2AutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public VertexAiPaLm2Api vertexAiApi(VertexAiPalm2ConnectionProperties connectionProperties,
			VertexAiPalm2EmbeddingProperties embeddingAiProperties, VertexAiPlam2ChatProperties chatProperties,
			RestClient.Builder restClientBuilder) {

		return new VertexAiPaLm2Api(connectionProperties.getBaseUrl(), connectionProperties.getApiKey(),
				chatProperties.getModel(), embeddingAiProperties.getModel(), restClientBuilder);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = VertexAiPlam2ChatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public VertexAiPaLm2ChatModel vertexAiChatModel(VertexAiPaLm2Api vertexAiApi,
			VertexAiPlam2ChatProperties chatProperties) {
		return new VertexAiPaLm2ChatModel(vertexAiApi, chatProperties.getOptions());
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = VertexAiPalm2EmbeddingProperties.CONFIG_PREFIX, name = "enabled",
			havingValue = "true", matchIfMissing = true)
	public VertexAiPaLm2EmbeddingModel vertexAiEmbeddingModel(VertexAiPaLm2Api vertexAiApi) {
		return new VertexAiPaLm2EmbeddingModel(vertexAiApi);
	}

}
