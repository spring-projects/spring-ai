/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.autoconfigure.vertexai;

import org.springframework.ai.autoconfigure.NativeHints;
import org.springframework.ai.vertex.api.VertexAiApi;
import org.springframework.ai.vertex.VertexAiEmbeddingClient;
import org.springframework.ai.vertex.VertexAiChatClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.web.client.RestClient;

@AutoConfiguration(after = RestClientAutoConfiguration.class)
@ConditionalOnClass(VertexAiApi.class)
@ImportRuntimeHints(NativeHints.class)
@EnableConfigurationProperties({ VertexAiConnectionProperties.class, VertexAiChatProperties.class,
		VertexAiEmbeddingProperties.class })
public class VertexAiAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public VertexAiChatClient vertexAiChatClient(VertexAiApi vertexAiApi, VertexAiChatProperties chatProperties) {

		VertexAiChatClient client = new VertexAiChatClient(vertexAiApi).withTemperature(chatProperties.getTemperature())
			.withTopP(chatProperties.getTopP())
			.withTopK(chatProperties.getTopK())
			.withCandidateCount(chatProperties.getCandidateCount());

		return client;
	}

	@Bean
	@ConditionalOnMissingBean
	public VertexAiEmbeddingClient vertexAiEmbeddingClient(VertexAiApi vertexAiApi) {
		return new VertexAiEmbeddingClient(vertexAiApi);
	}

	@Bean
	@ConditionalOnMissingBean
	public VertexAiApi vertexAiApi(VertexAiConnectionProperties connectionProperties,
			VertexAiEmbeddingProperties embeddingAiProperties, VertexAiChatProperties chatProperties,
			RestClient.Builder restClientBuilder) {

		return new VertexAiApi(connectionProperties.getBaseUrl(), connectionProperties.getApiKey(),
				chatProperties.getModel(), embeddingAiProperties.getModel(), restClientBuilder);
	}

}
