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
package org.springframework.ai.autoconfigure.vectorstore.milvus;

import java.util.concurrent.TimeUnit;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.MilvusVectorStore;
import org.springframework.ai.vectorstore.MilvusVectorStore.MilvusVectorStoreConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

/**
 * @author Christian Tzolov
 * @author Eddú Meléndez
 */
@AutoConfiguration
@ConditionalOnClass({ MilvusVectorStore.class, EmbeddingModel.class })
@EnableConfigurationProperties({ MilvusServiceClientProperties.class, MilvusVectorStoreProperties.class })
public class MilvusVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(MilvusServiceClientConnectionDetails.class)
	PropertiesMilvusServiceClientConnectionDetails milvusServiceClientConnectionDetails(
			MilvusServiceClientProperties properties) {
		return new PropertiesMilvusServiceClientConnectionDetails(properties);
	}

	@Bean
	@ConditionalOnMissingBean
	public MilvusVectorStore vectorStore(MilvusServiceClient milvusClient, EmbeddingModel embeddingModel,
			MilvusVectorStoreProperties properties) {

		MilvusVectorStoreConfig config = MilvusVectorStoreConfig.builder()
			.withCollectionName(properties.getCollectionName())
			.withDatabaseName(properties.getDatabaseName())
			.withIndexType(IndexType.valueOf(properties.getIndexType().name()))
			.withMetricType(MetricType.valueOf(properties.getMetricType().name()))
			.withIndexParameters(properties.getIndexParameters())
			.withEmbeddingDimension(properties.getEmbeddingDimension())
			.build();

		return new MilvusVectorStore(milvusClient, embeddingModel, config, properties.isInitializeSchema());
	}

	@Bean
	@ConditionalOnMissingBean
	public MilvusServiceClient milvusClient(MilvusVectorStoreProperties serverProperties,
			MilvusServiceClientProperties clientProperties, MilvusServiceClientConnectionDetails connectionDetails) {

		var builder = ConnectParam.newBuilder()
			.withHost(connectionDetails.getHost())
			.withPort(connectionDetails.getPort())
			.withDatabaseName(serverProperties.getDatabaseName())
			.withConnectTimeout(clientProperties.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
			.withKeepAliveTime(clientProperties.getKeepAliveTimeMs(), TimeUnit.MILLISECONDS)
			.withKeepAliveTimeout(clientProperties.getKeepAliveTimeoutMs(), TimeUnit.MILLISECONDS)
			.withRpcDeadline(clientProperties.getRpcDeadlineMs(), TimeUnit.MILLISECONDS)
			.withSecure(clientProperties.isSecure())
			.withIdleTimeout(clientProperties.getIdleTimeoutMs(), TimeUnit.MILLISECONDS)
			.withAuthorization(clientProperties.getUsername(), clientProperties.getPassword());

		if (clientProperties.isSecure() && StringUtils.hasText(clientProperties.getUri())) {
			builder.withUri(clientProperties.getUri());
		}

		if (clientProperties.isSecure() && StringUtils.hasText(clientProperties.getToken())) {
			builder.withToken(clientProperties.getToken());
		}

		if (clientProperties.isSecure() && StringUtils.hasText(clientProperties.getClientKeyPath())) {
			builder.withClientKeyPath(clientProperties.getClientKeyPath());
		}

		if (clientProperties.isSecure() && StringUtils.hasText(clientProperties.getClientPemPath())) {
			builder.withClientPemPath(clientProperties.getClientPemPath());
		}

		if (clientProperties.isSecure() && StringUtils.hasText(clientProperties.getCaPemPath())) {
			builder.withCaPemPath(clientProperties.getCaPemPath());
		}

		if (clientProperties.isSecure() && StringUtils.hasText(clientProperties.getServerPemPath())) {
			builder.withServerPemPath(clientProperties.getServerPemPath());
		}

		if (clientProperties.isSecure() && StringUtils.hasText(clientProperties.getServerName())) {
			builder.withServerName(clientProperties.getServerName());
		}

		return new MilvusServiceClient(builder.build());
	}

	private static class PropertiesMilvusServiceClientConnectionDetails
			implements MilvusServiceClientConnectionDetails {

		private final MilvusServiceClientProperties properties;

		PropertiesMilvusServiceClientConnectionDetails(MilvusServiceClientProperties properties) {
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
