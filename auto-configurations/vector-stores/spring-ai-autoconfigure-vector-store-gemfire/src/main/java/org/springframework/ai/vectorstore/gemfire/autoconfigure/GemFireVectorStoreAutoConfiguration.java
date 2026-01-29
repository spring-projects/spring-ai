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

package org.springframework.ai.vectorstore.gemfire.autoconfigure;

import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.SpringAIVectorStoreTypes;
import org.springframework.ai.vectorstore.gemfire.GemFireVectorStore;
import org.springframework.ai.vectorstore.gemfire.GemFireVectorStore.Builder;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for GemFire Vector Store.
 *
 * @author Geet Rawat
 * @author Christian Tzolov
 * @author Soby Chacko
 * @author Jason Huynh
 */
@AutoConfiguration
@ConditionalOnClass({ GemFireVectorStore.class, EmbeddingModel.class })
@EnableConfigurationProperties(GemFireVectorStoreProperties.class)
@ConditionalOnProperty(name = SpringAIVectorStoreTypes.TYPE, havingValue = SpringAIVectorStoreTypes.GEMFIRE,
		matchIfMissing = true)
public class GemFireVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(GemFireConnectionDetails.class)
	GemFireVectorStoreAutoConfiguration.PropertiesGemFireConnectionDetails gemfireConnectionDetails(
			GemFireVectorStoreProperties properties) {
		return new GemFireVectorStoreAutoConfiguration.PropertiesGemFireConnectionDetails(properties);
	}

	@Bean
	@ConditionalOnMissingBean
	BatchingStrategy batchingStrategy() {
		return new TokenCountBatchingStrategy();
	}

	@Bean
	@ConditionalOnMissingBean
	public GemFireVectorStore gemfireVectorStore(EmbeddingModel embeddingModel, GemFireVectorStoreProperties properties,
			GemFireConnectionDetails gemFireConnectionDetails, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
			BatchingStrategy batchingStrategy) {

		Builder builder = GemFireVectorStore.builder(embeddingModel)
			.host(gemFireConnectionDetails.getHost())
			.port(gemFireConnectionDetails.getPort())
			.indexName(properties.getIndexName())
			.beamWidth(properties.getBeamWidth())
			.maxConnections(properties.getMaxConnections())
			.buckets(properties.getBuckets())
			.vectorSimilarityFunction(properties.getVectorSimilarityFunction())
			.fields(properties.getFields())
			.sslEnabled(properties.isSslEnabled())
			.initializeSchema(properties.isInitializeSchema())
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.customObservationConvention(customObservationConvention.getIfAvailable(() -> null))
			.batchingStrategy(batchingStrategy);
		if (gemFireConnectionDetails.getUsername() != null) {
			builder.username(gemFireConnectionDetails.getUsername());
		}
		if (gemFireConnectionDetails.getPassword() != null) {
			builder.password(gemFireConnectionDetails.getPassword());
		}
		if (gemFireConnectionDetails.getToken() != null) {
			builder.token(gemFireConnectionDetails.getToken());
		}
		return builder.build();
	}

	private static class PropertiesGemFireConnectionDetails implements GemFireConnectionDetails {

		private final GemFireVectorStoreProperties properties;

		PropertiesGemFireConnectionDetails(GemFireVectorStoreProperties properties) {
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

		@Override
		public @Nullable String getUsername() {
			return this.properties.getUsername();
		}

		@Override
		public @Nullable String getPassword() {
			return this.properties.getPassword();
		}

		@Override
		public @Nullable String getToken() {
			return this.properties.getToken();
		}

	}

}
