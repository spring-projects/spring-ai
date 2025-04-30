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

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.jdbc.JdbcChatMemory;
import org.springframework.ai.chat.memory.jdbc.JdbcChatMemoryConfig;
import org.springframework.ai.chat.memory.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @author Jonathan Leijendekker
 * @author Thomas Vitale
 * @since 1.0.0
 */
@AutoConfiguration(after = JdbcTemplateAutoConfiguration.class, before = ChatMemoryAutoConfiguration.class)
@ConditionalOnClass({ JdbcChatMemoryRepository.class, DataSource.class, JdbcTemplate.class })
@EnableConfigurationProperties(JdbcChatMemoryProperties.class)
public class JdbcChatMemoryAutoConfiguration {

	private static final Logger logger = LoggerFactory.getLogger(JdbcChatMemoryAutoConfiguration.class);

	@Bean
	@ConditionalOnMissingBean
	JdbcChatMemoryRepository chatMemoryRepository(JdbcTemplate jdbcTemplate) {
		return JdbcChatMemoryRepository.builder().jdbcTemplate(jdbcTemplate).build();
	}

	/**
	 * @deprecated in favor of building a {@link MessageWindowChatMemory} (or other
	 * {@link ChatMemory} implementations) with a {@link JdbcChatMemoryRepository}
	 * instance.
	 */
	@Bean
	@ConditionalOnMissingBean
	@Deprecated
	JdbcChatMemory chatMemory(JdbcTemplate jdbcTemplate) {
		var config = JdbcChatMemoryConfig.builder().jdbcTemplate(jdbcTemplate).build();
		return JdbcChatMemory.create(config);
	}

	@Bean
	@ConditionalOnMissingBean
	@Conditional(OnSchemaInitializationEnabledCondition.class)
	JdbcChatMemoryDataSourceScriptDatabaseInitializer jdbcChatMemoryScriptDatabaseInitializer(DataSource dataSource) {
		logger.debug("Initializing schema for JdbcChatMemoryRepository");
		return new JdbcChatMemoryDataSourceScriptDatabaseInitializer(dataSource);
	}

	/**
	 * Condition to check if the schema initialization is enabled, supporting both
	 * deprecated and new property.
	 *
	 * @deprecated to be removed in 1.0.0-RC1.
	 */
	@Deprecated
	static class OnSchemaInitializationEnabledCondition extends SpringBootCondition {

		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
			Boolean initializeSchemaEnabled = context.getEnvironment()
				.getProperty("spring.ai.chat.memory.jdbc.initialize-schema", Boolean.class);

			if (initializeSchemaEnabled != null) {
				return new ConditionOutcome(initializeSchemaEnabled,
						ConditionMessage.forCondition("Enable JDBC Chat Memory Schema Initialization")
							.because("spring.ai.chat.memory.jdbc.initialize-schema is " + initializeSchemaEnabled));
			}

			initializeSchemaEnabled = context.getEnvironment()
				.getProperty(JdbcChatMemoryProperties.CONFIG_PREFIX + ".initialize-schema", Boolean.class, true);

			return new ConditionOutcome(initializeSchemaEnabled, ConditionMessage
				.forCondition("Enable JDBC Chat Memory Schema Initialization")
				.because(JdbcChatMemoryProperties.CONFIG_PREFIX + ".initialize-schema is " + initializeSchemaEnabled));
		}

	}

}
