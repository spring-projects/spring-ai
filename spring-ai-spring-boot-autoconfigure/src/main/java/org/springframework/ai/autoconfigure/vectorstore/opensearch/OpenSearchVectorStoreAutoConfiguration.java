/*
 * Copyright 2023-2024 the original author or authors.
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
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.OpenSearchVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.observation.ObservationRegistry;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

@AutoConfiguration
@ConditionalOnClass({ OpenSearchVectorStore.class, EmbeddingModel.class, OpenSearchClient.class })
@EnableConfigurationProperties(OpenSearchVectorStoreProperties.class)
public class OpenSearchVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(OpenSearchConnectionDetails.class)
	PropertiesOpenSearchConnectionDetails openSearchConnectionDetails(OpenSearchVectorStoreProperties properties) {
		return new PropertiesOpenSearchConnectionDetails(properties);
	}

	@Bean
	@ConditionalOnMissingBean(BatchingStrategy.class)
	BatchingStrategy batchingStrategy() {
		return new TokenCountBatchingStrategy();
	}

	@Bean
	@ConditionalOnMissingBean
	OpenSearchVectorStore vectorStore(OpenSearchVectorStoreProperties properties, OpenSearchClient openSearchClient,
			EmbeddingModel embeddingModel, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
			BatchingStrategy batchingStrategy) {
		var indexName = Optional.ofNullable(properties.getIndexName()).orElse(OpenSearchVectorStore.DEFAULT_INDEX_NAME);
		var mappingJson = Optional.ofNullable(properties.getMappingJson())
			.orElse(OpenSearchVectorStore.DEFAULT_MAPPING_EMBEDDING_TYPE_KNN_VECTOR_DIMENSION_1536);
		return new OpenSearchVectorStore(indexName, openSearchClient, embeddingModel, mappingJson,
				properties.isInitializeSchema(), observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP),
				customObservationConvention.getIfAvailable(() -> null), batchingStrategy);
	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingClass({ "software.amazon.awssdk.regions.Region",
			"software.amazon.awssdk.http.apache.ApacheHttpClient" })
	static class OpenSearchConfiguration {

		@Bean
		@ConditionalOnMissingBean
		OpenSearchClient openSearchClient(OpenSearchConnectionDetails connectionDetails) {
			HttpHost[] httpHosts = connectionDetails.getUris()
				.stream()
				.map(s -> createHttpHost(s))
				.toArray(HttpHost[]::new);
			ApacheHttpClient5TransportBuilder transportBuilder = ApacheHttpClient5TransportBuilder.builder(httpHosts);
			Optional.ofNullable(connectionDetails.getUsername())
				.map(username -> createBasicCredentialsProvider(httpHosts[0], username,
						connectionDetails.getPassword()))
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

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass({ Region.class, ApacheHttpClient.class })
	static class AwsOpenSearchConfiguration {

		@Bean
		@ConditionalOnMissingBean
		OpenSearchClient openSearchClient(OpenSearchVectorStoreProperties properties, AwsSdk2TransportOptions options) {
			OpenSearchVectorStoreProperties.Aws aws = properties.getAws();
			Region region = Region.of(aws.getRegion());

			SdkHttpClient httpClient = ApacheHttpClient.builder().build();
			OpenSearchTransport transport = new AwsSdk2Transport(httpClient, aws.getHost(), aws.getServiceName(),
					region, options);
			return new OpenSearchClient(transport);
		}

		@Bean
		@ConditionalOnMissingBean
		AwsSdk2TransportOptions options(OpenSearchVectorStoreProperties properties) {
			OpenSearchVectorStoreProperties.Aws aws = properties.getAws();
			return AwsSdk2TransportOptions.builder()
				.setCredentials(StaticCredentialsProvider
					.create(AwsBasicCredentials.create(aws.getAccessKey(), aws.getSecretKey())))
				.build();
		}

	}

	static class PropertiesOpenSearchConnectionDetails implements OpenSearchConnectionDetails {

		private final OpenSearchVectorStoreProperties properties;

		PropertiesOpenSearchConnectionDetails(OpenSearchVectorStoreProperties properties) {
			this.properties = properties;
		}

		@Override
		public List<String> getUris() {
			return this.properties.getUris();
		}

		@Override
		public String getUsername() {
			return this.properties.getUsername();
		}

		@Override
		public String getPassword() {
			return this.properties.getPassword();
		}

	}

}
