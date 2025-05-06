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

package org.springframework.ai.vectorstore.opensearch.autoconfigure;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.micrometer.observation.ObservationRegistry;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.nio.AsyncClientConnectionManager;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.SpringAIVectorStoreTypes;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.ai.vectorstore.opensearch.OpenSearchVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@AutoConfiguration
@ConditionalOnClass({ OpenSearchVectorStore.class, EmbeddingModel.class, OpenSearchClient.class })
@EnableConfigurationProperties(OpenSearchVectorStoreProperties.class)
@ConditionalOnProperty(name = SpringAIVectorStoreTypes.TYPE, havingValue = SpringAIVectorStoreTypes.OPENSEARCH,
		matchIfMissing = true)
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
			.orElse(OpenSearchVectorStore.DEFAULT_MAPPING_EMBEDDING_TYPE_KNN_VECTOR_DIMENSION);

		return OpenSearchVectorStore.builder(openSearchClient, embeddingModel)
			.index(indexName)
			.mappingJson(mappingJson)
			.initializeSchema(properties.isInitializeSchema())
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.customObservationConvention(customObservationConvention.getIfAvailable(() -> null))
			.batchingStrategy(batchingStrategy)
			.build();
	}

	@Configuration(proxyBeanMethods = false)
	@org.springframework.context.annotation.Conditional(OpenSearchNonAwsCondition.class)
	static class OpenSearchConfiguration {

		@Bean
		@ConditionalOnMissingBean
		OpenSearchClient openSearchClient(OpenSearchVectorStoreProperties properties, Optional<SslBundles> sslBundles) {
			HttpHost[] httpHosts = properties.getUris().stream().map(this::createHttpHost).toArray(HttpHost[]::new);
			Optional<BasicCredentialsProvider> basicCredentialsProvider = Optional.ofNullable(properties.getUsername())
				.map(username -> createBasicCredentialsProvider(httpHosts, username, properties.getPassword()));

			var transportBuilder = ApacheHttpClient5TransportBuilder.builder(httpHosts);
			transportBuilder.setHttpClientConfigCallback(httpClientBuilder -> {
				basicCredentialsProvider.ifPresent(httpClientBuilder::setDefaultCredentialsProvider);
				httpClientBuilder.setConnectionManager(createConnectionManager(properties, sslBundles));
				httpClientBuilder.setDefaultRequestConfig(createRequestConfig(properties));
				return httpClientBuilder;
			});

			return new OpenSearchClient(transportBuilder.build());
		}

		private AsyncClientConnectionManager createConnectionManager(OpenSearchVectorStoreProperties properties,
				Optional<SslBundles> sslBundles) {
			var connectionManagerBuilder = PoolingAsyncClientConnectionManagerBuilder.create();
			if (sslBundles.isPresent()) {
				Optional.ofNullable(properties.getSslBundle())
					.map(bundle -> sslBundles.get().getBundle(bundle))
					.map(bundle -> ClientTlsStrategyBuilder.create()
						.setSslContext(bundle.createSslContext())
						.setTlsVersions(bundle.getOptions().getEnabledProtocols())
						.build())
					.ifPresent(connectionManagerBuilder::setTlsStrategy);
			}
			return connectionManagerBuilder.build();
		}

		private RequestConfig createRequestConfig(OpenSearchVectorStoreProperties properties) {
			var requestConfigBuilder = RequestConfig.custom();
			Optional.ofNullable(properties.getConnectionTimeout())
				.map(Duration::toMillis)
				.ifPresent(timeoutMillis -> requestConfigBuilder.setConnectionRequestTimeout(timeoutMillis,
						TimeUnit.MILLISECONDS));
			Optional.ofNullable(properties.getReadTimeout())
				.map(Duration::toMillis)
				.ifPresent(
						timeoutMillis -> requestConfigBuilder.setResponseTimeout(timeoutMillis, TimeUnit.MILLISECONDS));
			return requestConfigBuilder.build();
		}

		private BasicCredentialsProvider createBasicCredentialsProvider(HttpHost[] httpHosts, String username,
				String password) {
			BasicCredentialsProvider basicCredentialsProvider = new BasicCredentialsProvider();
			for (HttpHost httpHost : httpHosts) {
				basicCredentialsProvider.setCredentials(new AuthScope(httpHost),
						new UsernamePasswordCredentials(username, password.toCharArray()));
			}
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

	/**
	 * AWS OpenSearch configuration.
	 * <p>
	 * This configuration is only enabled if AWS SDK classes are present on the classpath
	 * <b>and</b> the property {@code spring.ai.vectorstore.opensearch.aws.enabled} is set
	 * to {@code true} (default: true).
	 * <p>
	 * Set {@code spring.ai.vectorstore.opensearch.aws.enabled=false} to disable
	 * AWS-specific OpenSearch configuration when AWS SDK is present for other services
	 * (e.g., S3).
	 */
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnClass({ AwsCredentialsProvider.class, Region.class, ApacheHttpClient.class })
	@ConditionalOnProperty(name = "spring.ai.vectorstore.opensearch.aws.enabled", havingValue = "true",
			matchIfMissing = true)
	static class AwsOpenSearchConfiguration {

		@Bean
		@ConditionalOnMissingBean(AwsOpenSearchConnectionDetails.class)
		PropertiesAwsOpenSearchConnectionDetails awsOpenSearchConnectionDetails(
				OpenSearchVectorStoreProperties properties) {
			return new PropertiesAwsOpenSearchConnectionDetails(properties);
		}

		@Bean
		@ConditionalOnMissingBean
		OpenSearchClient openSearchClient(OpenSearchVectorStoreProperties properties, Optional<SslBundles> sslBundles,
				AwsOpenSearchConnectionDetails connectionDetails, AwsSdk2TransportOptions options) {
			Region region = Region.of(connectionDetails.getRegion());

			var httpClientBuilder = ApacheHttpClient.builder();
			Optional.ofNullable(properties.getConnectionTimeout()).ifPresent(httpClientBuilder::connectionTimeout);
			Optional.ofNullable(properties.getReadTimeout()).ifPresent(httpClientBuilder::socketTimeout);
			if (sslBundles.isPresent()) {
				Optional.ofNullable(properties.getSslBundle())
					.map(bundle -> sslBundles.get().getBundle(bundle))
					.ifPresent(bundle -> httpClientBuilder
						.tlsKeyManagersProvider(() -> bundle.getManagers().getKeyManagers())
						.tlsTrustManagersProvider(() -> bundle.getManagers().getTrustManagers()));
			}
			OpenSearchTransport transport = new AwsSdk2Transport(httpClientBuilder.build(),
					connectionDetails.getHost(properties.getAws().getDomainName()),
					properties.getAws().getServiceName(), region, options);
			return new OpenSearchClient(transport);
		}

		@Bean
		@ConditionalOnMissingBean
		AwsSdk2TransportOptions options(AwsOpenSearchConnectionDetails connectionDetails) {
			return AwsSdk2TransportOptions.builder()
				.setCredentials(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(connectionDetails.getAccessKey(), connectionDetails.getSecretKey())))
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

	static class PropertiesAwsOpenSearchConnectionDetails implements AwsOpenSearchConnectionDetails {

		private final OpenSearchVectorStoreProperties.Aws aws;

		PropertiesAwsOpenSearchConnectionDetails(OpenSearchVectorStoreProperties properties) {
			this.aws = properties.getAws();
		}

		@Override
		public String getRegion() {
			return this.aws.getRegion();
		}

		@Override
		public String getAccessKey() {
			return this.aws.getAccessKey();
		}

		@Override
		public String getSecretKey() {
			return this.aws.getSecretKey();
		}

		@Override
		public String getHost(String domainName) {
			if (StringUtils.hasText(domainName)) {
				return "%s.%s".formatted(this.aws.getDomainName(), this.aws.getHost());
			}
			return this.aws.getHost();
		}

	}

}
