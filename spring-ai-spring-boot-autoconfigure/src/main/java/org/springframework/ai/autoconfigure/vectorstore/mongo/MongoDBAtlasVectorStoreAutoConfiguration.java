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

package org.springframework.ai.autoconfigure.vectorstore.mongo;

import java.util.Arrays;

import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.MongoDBAtlasVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.util.MimeType;
import org.springframework.util.StringUtils;

/**
 * @author Eddú Meléndez
 * @author Christian Tzolov
 * @author Soby Chacko
 * @author Ignacio López
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass({ MongoDBAtlasVectorStore.class, EmbeddingModel.class, MongoTemplate.class })
@EnableConfigurationProperties(MongoDBAtlasVectorStoreProperties.class)
public class MongoDBAtlasVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(BatchingStrategy.class)
	BatchingStrategy batchingStrategy() {
		return new TokenCountBatchingStrategy();
	}

	@Bean
	@ConditionalOnMissingBean
	MongoDBAtlasVectorStore vectorStore(MongoTemplate mongoTemplate, EmbeddingModel embeddingModel,
			MongoDBAtlasVectorStoreProperties properties, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<VectorStoreObservationConvention> customObservationConvention,
			BatchingStrategy batchingStrategy) {

		var builder = MongoDBAtlasVectorStore.MongoDBVectorStoreConfig.builder();

		if (StringUtils.hasText(properties.getCollectionName())) {
			builder.withCollectionName(properties.getCollectionName());
		}
		if (StringUtils.hasText(properties.getPathName())) {
			builder.withPathName(properties.getPathName());
		}
		if (StringUtils.hasText(properties.getIndexName())) {
			builder.withVectorIndexName(properties.getIndexName());
		}
		if (!properties.getMetadataFieldsToFilter().isEmpty()) {
			builder.withMetadataFieldsToFilter(properties.getMetadataFieldsToFilter());
		}
		MongoDBAtlasVectorStore.MongoDBVectorStoreConfig config = builder.build();

		return new MongoDBAtlasVectorStore(mongoTemplate, embeddingModel, config, properties.isInitializeSchema(),
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP),
				customObservationConvention.getIfAvailable(() -> null), batchingStrategy);
	}

	@Bean
	public Converter<MimeType, String> mimeTypeToStringConverter() {
		return new Converter<MimeType, String>() {

			@Override
			public String convert(MimeType source) {
				return source.toString();
			}
		};
	}

	@Bean
	public Converter<String, MimeType> stringToMimeTypeConverter() {
		return new Converter<String, MimeType>() {

			@Override
			public MimeType convert(String source) {
				return MimeType.valueOf(source);
			}
		};
	}

	@Bean
	public MongoCustomConversions mongoCustomConversions() {
		return new MongoCustomConversions(Arrays.asList(mimeTypeToStringConverter(), stringToMimeTypeConverter()));
	}

}
