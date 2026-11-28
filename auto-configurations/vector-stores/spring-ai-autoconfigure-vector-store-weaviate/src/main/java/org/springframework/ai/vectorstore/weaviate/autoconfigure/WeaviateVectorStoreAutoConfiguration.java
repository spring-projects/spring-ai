/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.vectorstore.weaviate.autoconfigure;

import java.util.Map;

import io.micrometer.observation.ObservationRegistry;
import io.weaviate.client6.v1.api.Authentication;
import io.weaviate.client6.v1.api.WeaviateClient;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.SpringAIVectorStoreTypes;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.ai.vectorstore.weaviate.WeaviateVectorStore;
import org.springframework.ai.vectorstore.weaviate.WeaviateVectorStoreOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * {@link AutoConfiguration Auto-configuration} for Weaviate Vector Store.
 *
 * @author Christian Tzolov
 * @author Eddú Meléndez
 * @author Soby Chacko
 * @author Jonghoon Park
 */
@AutoConfiguration
@ConditionalOnClass({ EmbeddingModel.class, WeaviateVectorStore.class })
@EnableConfigurationProperties(WeaviateVectorStoreProperties.class)
@ConditionalOnProperty(name = SpringAIVectorStoreTypes.TYPE, havingValue = SpringAIVectorStoreTypes.WEAVIATE,
		matchIfMissing = true)
public class WeaviateVectorStoreAutoConfiguration {

	private static final int DEFAULT_HTTP_PORT = 8080;

	private static final int DEFAULT_HTTPS_PORT = 443;

	@Bean
	@ConditionalOnMissingBean(WeaviateConnectionDetails.class)
	PropertiesWeaviateConnectionDetails weaviateConnectionDetails(WeaviateVectorStoreProperties properties) {
		return new PropertiesWeaviateConnectionDetails(properties);
	}

	@Bean(destroyMethod = "close")
	@ConditionalOnMissingBean
	public WeaviateClient weaviateClient(WeaviateVectorStoreProperties properties,
			WeaviateConnectionDetails connectionDetails) {
		String scheme = properties.getScheme();
		boolean secure = "https".equalsIgnoreCase(scheme);
		HostPort http = HostPort.parse(connectionDetails.getHost(), secure ? DEFAULT_HTTPS_PORT : DEFAULT_HTTP_PORT);
		HostPort grpc = HostPort.parse(connectionDetails.getGrpcHost(), WeaviateConnectionDetails.DEFAULT_GRPC_PORT);
		String apiKey = properties.getApiKey();
		Map<String, String> headers = properties.getHeaders();

		return WeaviateClient.connectToCustom(custom -> {
			// scheme must be set first: Config.Custom#scheme resets the HTTP/gRPC ports.
			custom.scheme(scheme);
			custom.httpHost(http.host());
			custom.httpPort(http.port());
			custom.grpcHost(grpc.host());
			custom.grpcPort(grpc.port());
			if (StringUtils.hasText(apiKey)) {
				custom.authentication(Authentication.apiKey(apiKey));
			}
			if (!CollectionUtils.isEmpty(headers)) {
				custom.setHeaders(headers);
			}
			return custom;
		});
	}

	@Bean
	@ConditionalOnMissingBean
	BatchingStrategy batchingStrategy() {
		return new TokenCountBatchingStrategy();
	}

	@Bean
	@ConditionalOnMissingBean
	public WeaviateVectorStore vectorStore(EmbeddingModel embeddingModel, WeaviateClient weaviateClient,
			WeaviateVectorStoreProperties properties, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
			BatchingStrategy batchingStrategy) {
		return WeaviateVectorStore.builder(weaviateClient, embeddingModel)
			.options(mappingPropertiesToOptions(properties))
			.filterMetadataFields(properties.getFilterField()
				.entrySet()
				.stream()
				.map(e -> new WeaviateVectorStore.MetadataField(e.getKey(), e.getValue()))
				.toList())
			.consistencyLevel(WeaviateVectorStore.ConsistentLevel.valueOf(properties.getConsistencyLevel().name()))
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.customObservationConvention(customObservationConvention.getIfAvailable())
			.batchingStrategy(batchingStrategy)
			.build();
	}

	WeaviateVectorStoreOptions mappingPropertiesToOptions(WeaviateVectorStoreProperties properties) {
		WeaviateVectorStoreOptions weaviateVectorStoreOptions = new WeaviateVectorStoreOptions();

		PropertyMapper mapper = PropertyMapper.get();
		mapper.from(properties::getContentFieldName).whenHasText().to(weaviateVectorStoreOptions::setContentFieldName);
		mapper.from(properties::getObjectClass).whenHasText().to(weaviateVectorStoreOptions::setObjectClass);
		mapper.from(properties::getMetaFieldPrefix).whenHasText().to(weaviateVectorStoreOptions::setMetaFieldPrefix);

		return weaviateVectorStoreOptions;
	}

	/**
	 * A {@code host:port} pair, tolerant of a missing port.
	 */
	private record HostPort(String host, int port) {

		static HostPort parse(String value, int defaultPort) {
			if (!StringUtils.hasText(value)) {
				return new HostPort("localhost", defaultPort);
			}
			int separator = value.lastIndexOf(':');
			if (separator < 0) {
				return new HostPort(value, defaultPort);
			}
			String host = value.substring(0, separator);
			String port = value.substring(separator + 1);
			return new HostPort(StringUtils.hasText(host) ? host : "localhost",
					StringUtils.hasText(port) ? Integer.parseInt(port) : defaultPort);
		}

	}

	static class PropertiesWeaviateConnectionDetails implements WeaviateConnectionDetails {

		private final WeaviateVectorStoreProperties properties;

		PropertiesWeaviateConnectionDetails(WeaviateVectorStoreProperties properties) {
			this.properties = properties;
		}

		@Override
		public String getHost() {
			return this.properties.getHost();
		}

		@Override
		public String getGrpcHost() {
			String grpcHost = this.properties.getGrpcHost();
			String host = StringUtils.hasText(grpcHost) ? grpcHost
					: HostPort.parse(this.properties.getHost(), DEFAULT_HTTP_PORT).host();
			return host + ":" + this.properties.getGrpcPort();
		}

	}

}
