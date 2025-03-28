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

package org.springframework.ai.model.chat.memory.jdbc.autoconfigure;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.memory.jdbc.JdbcChatMemory;
import org.springframework.ai.chat.memory.jdbc.JdbcChatMemoryConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @author Jonathan Leijendekker
 * @since 1.0.0
 */
@AutoConfiguration(after = JdbcTemplateAutoConfiguration.class)
@ConditionalOnClass({ JdbcChatMemory.class, DataSource.class, JdbcTemplate.class })
@EnableConfigurationProperties(JdbcChatMemoryProperties.class)
public class JdbcChatMemoryAutoConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(JdbcChatMemoryAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	public JdbcChatMemory chatMemory(JdbcTemplate jdbcTemplate) {
		var config = JdbcChatMemoryConfig.builder().jdbcTemplate(jdbcTemplate).build();

		return JdbcChatMemory.create(config);
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(value = "spring.ai.chat.memory.jdbc.initialize-schema", havingValue = "true",
			matchIfMissing = true)
	public DataSourceScriptDatabaseInitializer jdbcChatMemoryScriptDatabaseInitializer(DataSource dataSource) {
		logger.debug("Initializing JdbcChatMemory schema");

		return new JdbcChatMemoryDataSourceScriptDatabaseInitializer(dataSource);
	}

}
