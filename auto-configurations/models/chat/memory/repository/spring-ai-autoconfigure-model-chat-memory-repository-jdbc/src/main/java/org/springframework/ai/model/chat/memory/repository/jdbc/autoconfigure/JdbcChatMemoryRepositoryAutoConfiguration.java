/*
 * Copyright 2024-2025 the original author or authors.
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

package org.springframework.ai.model.chat.memory.repository.jdbc.autoconfigure;

import javax.sql.DataSource;

import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepositoryDialect;
import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.OnDatabaseInitializationCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @author Jonathan Leijendekker
 * @author Thomas Vitale
 * @author Yanming Zhou
 * @since 1.0.0
 */
@AutoConfiguration(after = JdbcTemplateAutoConfiguration.class, before = ChatMemoryAutoConfiguration.class)
@ConditionalOnClass({ JdbcChatMemoryRepository.class, DataSource.class, JdbcTemplate.class })
@EnableConfigurationProperties(JdbcChatMemoryRepositoryProperties.class)
public class JdbcChatMemoryRepositoryAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	JdbcChatMemoryRepository jdbcChatMemoryRepository(JdbcTemplate jdbcTemplate, DataSource dataSource) {
		JdbcChatMemoryRepositoryDialect dialect = JdbcChatMemoryRepositoryDialect.from(dataSource);
		return JdbcChatMemoryRepository.builder().jdbcTemplate(jdbcTemplate).dialect(dialect).build();
	}

	@Bean
	@ConditionalOnMissingBean
	@Conditional(OnJdbcChatMemoryRepositoryDatasourceInitializationCondition.class)
	JdbcChatMemoryRepositorySchemaInitializer jdbcChatMemoryScriptDatabaseInitializer(DataSource dataSource,
			JdbcChatMemoryRepositoryProperties properties) {
		return new JdbcChatMemoryRepositorySchemaInitializer(dataSource, properties);
	}

	static class OnJdbcChatMemoryRepositoryDatasourceInitializationCondition extends OnDatabaseInitializationCondition {

		OnJdbcChatMemoryRepositoryDatasourceInitializationCondition() {
			super("Jdbc Chat Memory Repository",
					JdbcChatMemoryRepositoryProperties.CONFIG_PREFIX + ".initialize-schema");
		}

	}

}
