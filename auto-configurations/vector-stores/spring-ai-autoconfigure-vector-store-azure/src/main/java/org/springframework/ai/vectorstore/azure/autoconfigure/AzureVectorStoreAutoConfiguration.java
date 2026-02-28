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

package org.springframework.ai.vectorstore.azure.autoconfigure;

import java.util.List;
import java.util.stream.Collectors;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.ClientOptions;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.SpringAIVectorStoreTypes;
import org.springframework.ai.vectorstore.azure.AzureVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for Azure Vector Store.
 *
 * @author Christian Tzolov
 * @author Soby Chacko
 * @author Alexandros Pappas
 */
@AutoConfiguration
@ConditionalOnClass({ EmbeddingModel.class, SearchIndexClient.class, AzureVectorStore.class })
@EnableConfigurationProperties(AzureVectorStoreProperties.class)
@ConditionalOnProperty(name = SpringAIVectorStoreTypes.TYPE, havingValue = SpringAIVectorStoreTypes.AZURE,
		matchIfMissing = true)
public class AzureVectorStoreAutoConfiguration {

	private static final String APPLICATION_ID = "spring-ai";

	@Bean
	@ConditionalOnMissingBean
	public SearchIndexClient searchIndexClient(AzureVectorStoreProperties properties) {
		ClientOptions clientOptions = new ClientOptions();
		clientOptions.setApplicationId(APPLICATION_ID);
		if (properties.isUseKeylessAuth()) {
			return new SearchIndexClientBuilder().endpoint(properties.getUrl())
				.credential(new DefaultAzureCredentialBuilder().build())
				.clientOptions(clientOptions)
				.buildClient();
		}
		else {
			return new SearchIndexClientBuilder().endpoint(properties.getUrl())
				.credential(new AzureKeyCredential(properties.getApiKey()))
				.clientOptions(clientOptions)
				.buildClient();
		}
	}

	@Bean
	@ConditionalOnMissingBean
	BatchingStrategy batchingStrategy() {
		return new TokenCountBatchingStrategy();
	}

	@Bean
	@ConditionalOnMissingBean
	public AzureVectorStore vectorStore(SearchIndexClient searchIndexClient, EmbeddingModel embeddingModel,
			AzureVectorStoreProperties properties, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
			BatchingStrategy batchingStrategy) {

		var builder = AzureVectorStore.builder(searchIndexClient, embeddingModel)
			.initializeSchema(properties.isInitializeSchema())
			.filterMetadataFields(toMetadataFields(properties.getMetadataFields()))
			.observationRegistry(observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP))
			.customObservationConvention(customObservationConvention.getIfAvailable(() -> null))
			.batchingStrategy(batchingStrategy)
			.indexName(properties.getIndexName());

		if (properties.getDefaultTopK() >= 0) {
			builder.defaultTopK(properties.getDefaultTopK());
		}

		if (properties.getDefaultSimilarityThreshold() >= 0.0) {
			builder.defaultSimilarityThreshold(properties.getDefaultSimilarityThreshold());
		}

		if (properties.getContentFieldName() != null) {
			builder.contentFieldName(properties.getContentFieldName());
		}

		if (properties.getEmbeddingFieldName() != null) {
			builder.embeddingFieldName(properties.getEmbeddingFieldName());
		}

		if (properties.getMetadataFieldName() != null) {
			builder.metadataFieldName(properties.getMetadataFieldName());
		}

		return builder.build();
	}

	private static List<AzureVectorStore.MetadataField> toMetadataFields(
			List<AzureVectorStoreProperties.MetadataFieldEntry> entries) {
		if (entries == null || entries.isEmpty()) {
			return List.of();
		}
		return entries.stream()
			.filter(e -> e.getName() != null && !e.getName().isBlank() && e.getFieldType() != null
					&& !e.getFieldType().isBlank())
			.map(e -> toMetadataField(e.getName().trim(), e.getFieldType().trim()))
			.collect(Collectors.toList());
	}

	private static AzureVectorStore.MetadataField toMetadataField(String name, String fieldType) {
		return switch (fieldType.toLowerCase()) {
			case "string", "text" -> AzureVectorStore.MetadataField.text(name);
			case "int32" -> AzureVectorStore.MetadataField.int32(name);
			case "int64", "long" -> AzureVectorStore.MetadataField.int64(name);
			case "decimal", "double" -> AzureVectorStore.MetadataField.decimal(name);
			case "bool", "boolean" -> AzureVectorStore.MetadataField.bool(name);
			case "date", "datetime", "datetimeoffset" -> AzureVectorStore.MetadataField.date(name);
			default -> throw new IllegalArgumentException(
					"Unsupported metadata field type: '%s'. Supported types: string, int32, int64, decimal, bool, date"
						.formatted(fieldType));
		};
	}

}
