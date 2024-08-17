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

package org.springframework.ai.autoconfigure.vectorstore.gemfire;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.GemFireVectorStore;
import org.springframework.ai.vectorstore.GemFireVectorStoreConfig;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import io.micrometer.observation.ObservationRegistry;

/**
 * @author Geet Rawat
 * @author Christian Tzolov
 */
@AutoConfiguration
@ConditionalOnClass({ GemFireVectorStore.class, EmbeddingModel.class })
@EnableConfigurationProperties(GemFireVectorStoreProperties.class)
@ConditionalOnProperty(prefix = "spring.ai.vectorstore.gemfire", value = { "index-name" })
public class GemFireVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(GemFireConnectionDetails.class)
	GemFireVectorStoreAutoConfiguration.PropertiesGemFireConnectionDetails gemfireConnectionDetails(
			GemFireVectorStoreProperties properties) {
		return new GemFireVectorStoreAutoConfiguration.PropertiesGemFireConnectionDetails(properties);
	}

	@Bean
	@ConditionalOnMissingBean
	public GemFireVectorStore gemfireVectorStore(EmbeddingModel embeddingModel, GemFireVectorStoreProperties properties,
			GemFireConnectionDetails gemFireConnectionDetails, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<VectorStoreObservationConvention> customObservationConvention) {
		var config = new GemFireVectorStoreConfig();

		config.setHost(gemFireConnectionDetails.getHost())
			.setPort(gemFireConnectionDetails.getPort())
			.setIndexName(properties.getIndexName())
			.setBeamWidth(properties.getBeamWidth())
			.setMaxConnections(properties.getMaxConnections())
			.setBuckets(properties.getBuckets())
			.setVectorSimilarityFunction(properties.getVectorSimilarityFunction())
			.setFields(properties.getFields())
			.setSslEnabled(properties.isSslEnabled());
		return new GemFireVectorStore(config, embeddingModel, properties.isInitializeSchema(),
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP),
				customObservationConvention.getIfAvailable(() -> null));
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

	}

}
