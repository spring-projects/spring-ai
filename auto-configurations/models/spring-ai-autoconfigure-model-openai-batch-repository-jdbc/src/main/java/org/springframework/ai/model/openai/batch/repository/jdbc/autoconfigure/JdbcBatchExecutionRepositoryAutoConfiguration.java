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

package org.springframework.ai.model.openai.batch.repository.jdbc.autoconfigure;

import javax.sql.DataSource;

import org.springframework.ai.model.openai.autoconfigure.OpenAiBatchAutoConfiguration;
import org.springframework.ai.openai.batch.BatchExecutionRepository;
import org.springframework.ai.openai.batch.repository.jdbc.JdbcBatchExecutionRepository;
import org.springframework.ai.openai.batch.repository.jdbc.JdbcBatchExecutionRepositoryDialect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.sql.autoconfigure.init.OnDatabaseInitializationCondition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Auto-configuration for JDBC-based {@link BatchExecutionRepository}.
 *
 * @author Yasin Akbas
 * @since 2.0.0
 */
@AutoConfiguration(before = OpenAiBatchAutoConfiguration.class)
@ConditionalOnClass({ JdbcBatchExecutionRepository.class, DataSource.class, JdbcTemplate.class })
@EnableConfigurationProperties(JdbcBatchExecutionRepositoryProperties.class)
public class JdbcBatchExecutionRepositoryAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(BatchExecutionRepository.class)
	JdbcBatchExecutionRepository jdbcBatchExecutionRepository(JdbcTemplate jdbcTemplate, DataSource dataSource) {
		JdbcBatchExecutionRepositoryDialect dialect = JdbcBatchExecutionRepositoryDialect.from(dataSource);
		return JdbcBatchExecutionRepository.builder().jdbcTemplate(jdbcTemplate).dialect(dialect).build();
	}

	@Bean
	@ConditionalOnMissingBean
	@Conditional(OnJdbcBatchExecutionRepositoryDatasourceInitializationCondition.class)
	JdbcBatchExecutionRepositorySchemaInitializer jdbcBatchExecutionScriptDatabaseInitializer(DataSource dataSource,
			JdbcBatchExecutionRepositoryProperties properties) {
		return new JdbcBatchExecutionRepositorySchemaInitializer(dataSource, properties);
	}

	static class OnJdbcBatchExecutionRepositoryDatasourceInitializationCondition
			extends OnDatabaseInitializationCondition {

		OnJdbcBatchExecutionRepositoryDatasourceInitializationCondition() {
			super("Jdbc Batch Execution Repository",
					JdbcBatchExecutionRepositoryProperties.CONFIG_PREFIX + ".initialize-schema");
		}

	}

}
