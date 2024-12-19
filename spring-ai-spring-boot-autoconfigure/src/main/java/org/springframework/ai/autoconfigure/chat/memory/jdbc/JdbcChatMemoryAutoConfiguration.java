/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.autoconfigure.chat.memory.jdbc;

import javax.sql.DataSource;

import org.springframework.ai.chat.memory.JdbcChatMemory;
import org.springframework.ai.chat.memory.JdbcChatMemoryConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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

	@Bean
	@ConditionalOnMissingBean
	public JdbcChatMemory chatMemory(JdbcChatMemoryProperties properties, JdbcTemplate jdbcTemplate) {
		var config = JdbcChatMemoryConfig.builder()
			.setInitializeSchema(properties.isInitializeSchema())
			.jdbcTemplate(jdbcTemplate)
			.build();

		return JdbcChatMemory.create(config);
	}

}
