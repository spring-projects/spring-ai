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
package org.springframework.ai.autoconfigure.vectorstore.oracle;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.OracleVectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * @author Loïc Lefèvre
 */
@AutoConfiguration(after = JdbcTemplateAutoConfiguration.class)
@ConditionalOnClass({ OracleVectorStore.class, DataSource.class, JdbcTemplate.class })
@EnableConfigurationProperties(OracleAIVectorSearchStoreProperties.class)
public class OracleAIVectorSearchStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public OracleVectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel,
			OracleAIVectorSearchStoreProperties properties) {
		return new OracleVectorStore(jdbcTemplate, embeddingModel, properties.getTableName(), properties.getIndexType(),
				properties.getDistanceType(), properties.getDimensions(), properties.getSearchAccuracy(),
				properties.isInitializeSchema(), properties.isRemoveExistingVectorStoreTable(),
				properties.isForcedNormalization());
	}

}
