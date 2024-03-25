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
package org.springframework.ai.autoconfigure.vectorstore.opensearch;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.OpenSearchVectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.net.URISyntaxException;
import java.util.Optional;

@AutoConfiguration
@ConditionalOnClass({ OpenSearchVectorStore.class, EmbeddingModel.class, OpenSearchClient.class })
@EnableConfigurationProperties(OpenSearchVectorStoreProperties.class)
class OpenSearchVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	OpenSearchVectorStore vectorStore(OpenSearchVectorStoreProperties properties, OpenSearchClient openSearchClient,
			EmbeddingModel embeddingModel) {
		return new OpenSearchVectorStore(
				Optional.ofNullable(properties.getIndexName()).orElse(OpenSearchVectorStore.DEFAULT_INDEX_NAME),
				openSearchClient, embeddingModel, Optional.ofNullable(properties.getMappingJson())
					.orElse(OpenSearchVectorStore.DEFAULT_MAPPING_EMBEDDING_TYPE_KNN_VECTOR_DIMENSION_1536));
	}

	@Bean
	@ConditionalOnMissingBean
	OpenSearchClient openSearchClient(OpenSearchVectorStoreProperties properties) {
		HttpHost[] httpHosts = properties.getUris().stream().map(s -> createHttpHost(s)).toArray(HttpHost[]::new);
		ApacheHttpClient5TransportBuilder transportBuilder = ApacheHttpClient5TransportBuilder.builder(httpHosts);

		Optional.ofNullable(properties.getUsername())
			.map(username -> createBasicCredentialsProvider(httpHosts[0], username, properties.getPassword()))
			.ifPresent(basicCredentialsProvider -> transportBuilder
				.setHttpClientConfigCallback(httpAsyncClientBuilder -> httpAsyncClientBuilder
					.setDefaultCredentialsProvider(basicCredentialsProvider)));

		return new OpenSearchClient(transportBuilder.build());
	}

	private BasicCredentialsProvider createBasicCredentialsProvider(HttpHost httpHost, String username,
			String password) {
		BasicCredentialsProvider basicCredentialsProvider = new BasicCredentialsProvider();
		basicCredentialsProvider.setCredentials(new AuthScope(httpHost),
				new UsernamePasswordCredentials(username, password.toCharArray()));
		return basicCredentialsProvider;
	}

	private HttpHost createHttpHost(String s) {
		try {
			return HttpHost.create(s);
		}
		catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

}
