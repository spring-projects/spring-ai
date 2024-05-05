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
package org.springframework.ai.autoconfigure.vectorstore.hanadb;

import javax.sql.DataSource;

import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.HanaCloudVectorStore;
import org.springframework.ai.vectorstore.HanaCloudVectorStoreConfig;
import org.springframework.ai.vectorstore.HanaVectorEntity;
import org.springframework.ai.vectorstore.HanaVectorRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @author Rahul Mittal
 * @since 1.0.0
 */
@AutoConfiguration(after = { JpaRepositoriesAutoConfiguration.class })
@ConditionalOnClass({ HanaCloudVectorStore.class, DataSource.class, HanaVectorEntity.class })
@EnableConfigurationProperties(HanaCloudVectorStoreProperties.class)
public class HanaCloudVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public HanaCloudVectorStore vectorStore(HanaVectorRepository<? extends HanaVectorEntity> repository,
			EmbeddingClient embeddingClient, HanaCloudVectorStoreProperties properties) {

		return new HanaCloudVectorStore(repository, embeddingClient,
				HanaCloudVectorStoreConfig.builder()
					.tableName(properties.getTableName())
					.topK(properties.getTopK())
					.build());
	}

}