/*
 * Copyright 2024 - 2024 the original author or authors.
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
package org.springframework.ai.autoconfigure.vectorstore.cassandra;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

import com.google.common.base.Preconditions;

import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.CassandraVectorStore;
import org.springframework.ai.vectorstore.CassandraVectorStoreConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @author Mick Semb Wever
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass({ CassandraVectorStore.class, EmbeddingClient.class })
@EnableConfigurationProperties(CassandraVectorStoreProperties.class)
public class CassandraVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(CassandraConnectionDetails.class)
	public PropertiesCassandraConnectionDetails cassandraConnectionDetails(CassandraVectorStoreProperties properties) {
		return new PropertiesCassandraConnectionDetails(properties);
	}

	@Bean
	@ConditionalOnMissingBean
	public CassandraVectorStore vectorStore(EmbeddingClient embeddingClient, CassandraVectorStoreProperties properties,
			CassandraConnectionDetails cassandraConnectionDetails) {

		var builder = CassandraVectorStoreConfig.builder();
		if (cassandraConnectionDetails.hasCassandraContactPoints()) {
			for (InetSocketAddress contactPoint : cassandraConnectionDetails.getCassandraContactPoints()) {
				builder = builder.addContactPoint(contactPoint);
			}
		}
		if (cassandraConnectionDetails.hasCassandraLocalDatacenter()) {
			builder = builder.withLocalDatacenter(cassandraConnectionDetails.getCassandraLocalDatacenter());
		}

		builder = builder.withKeyspaceName(properties.getKeyspace())
			.withTableName(properties.getTable())
			.withContentColumnName(properties.getContentFieldName())
			.withEmbeddingColumnName(properties.getEmbeddingFieldName())
			.withIndexName(properties.getIndexName());

		if (properties.getDisallowSchemaCreation()) {
			builder = builder.disallowSchemaChanges();
		}

		return new CassandraVectorStore(builder.build(), embeddingClient);
	}

	private static class PropertiesCassandraConnectionDetails implements CassandraConnectionDetails {

		private final CassandraVectorStoreProperties properties;

		public PropertiesCassandraConnectionDetails(CassandraVectorStoreProperties properties) {
			this.properties = properties;
		}

		private String[] getCassandraContactPointHosts() {
			return this.properties.getCassandraContactPointHosts().split("(,| )");
		}

		@Override
		public List<InetSocketAddress> getCassandraContactPoints() {

			Preconditions.checkState(hasCassandraContactPoints(), "cassandraContactPointHosts has not been set");
			final int port = this.properties.getCassandraContactPointPort();

			return Arrays.asList(getCassandraContactPointHosts())
				.stream()
				.map((host) -> InetSocketAddress.createUnresolved(host, port))
				.toList();
		}

		@Override
		public String getCassandraLocalDatacenter() {
			Preconditions.checkState(hasCassandraLocalDatacenter(), "cassandraLocalDatacenter has not been set");
			return this.properties.getCassandraLocalDatacenter();
		}

		@Override
		public boolean hasCassandraContactPoints() {
			return null != this.properties.getCassandraContactPointHosts();
		}

		@Override
		public boolean hasCassandraLocalDatacenter() {
			return null != this.properties.getCassandraLocalDatacenter();
		}

	}

}
