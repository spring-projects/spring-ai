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
package org.springframework.ai.autoconfigure.vectorstore.chroma;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.chroma.ChromaApi;
import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorsore.ChromaVectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * @author Christian Tzolov
 * @author Eddú Meléndez
 */
@AutoConfiguration
@ConditionalOnClass({ EmbeddingClient.class, RestTemplate.class, ChromaVectorStore.class, ObjectMapper.class })
@EnableConfigurationProperties({ ChromaApiProperties.class, ChromaVectorStoreProperties.class })
public class ChromaVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(ChromaConnectionDetails.class)
	PropertiesChromaConnectionDetails chromaConnectionDetails(ChromaApiProperties properties) {
		return new PropertiesChromaConnectionDetails(properties);
	}

	@Bean
	@ConditionalOnMissingBean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}

	@Bean
	@ConditionalOnMissingBean
	public ChromaApi chromaApi(ChromaApiProperties apiProperties, RestTemplate restTemplate,
			ChromaConnectionDetails connectionDetails) {

		String chromaUrl = String.format("%s:%s", connectionDetails.getHost(), connectionDetails.getPort());

		var chromaApi = new ChromaApi(chromaUrl, restTemplate, new ObjectMapper());

		if (StringUtils.hasText(apiProperties.getKeyToken())) {
			chromaApi.withKeyToken(apiProperties.getKeyToken());
		}
		else if (StringUtils.hasText(apiProperties.getUsername()) && StringUtils.hasText(apiProperties.getPassword())) {
			chromaApi.withBasicAuthCredentials(apiProperties.getUsername(), apiProperties.getPassword());
		}

		return chromaApi;
	}

	@Bean
	@ConditionalOnMissingBean
	public ChromaVectorStore vectorStore(EmbeddingClient embeddingClient, ChromaApi chromaApi,
			ChromaVectorStoreProperties storeProperties) {
		return new ChromaVectorStore(embeddingClient, chromaApi, storeProperties.getCollectionName());
	}

	private static class PropertiesChromaConnectionDetails implements ChromaConnectionDetails {

		private final ChromaApiProperties properties;

		PropertiesChromaConnectionDetails(ChromaApiProperties properties) {
			this.properties = properties;
		}

		@Override
		public String getHost() {
			return this.properties.getHost();
		}

		@Override
		public int getPort() {
			return this.properties.getPort();
		}

	}

}
