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

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jonathan Leijendekker
 */
@Testcontainers
class JdbcChatMemoryRepositorySchemaInitializerPostgresqlTests {

	static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("postgres:17");

	@Container
	@SuppressWarnings("resource")
	static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(DEFAULT_IMAGE_NAME)
		.withDatabaseName("chat_memory_initializer_test")
		.withUsername("postgres")
		.withPassword("postgres");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JdbcChatMemoryRepositoryAutoConfiguration.class,
				JdbcTemplateAutoConfiguration.class, DataSourceAutoConfiguration.class))
		.withPropertyValues(String.format("spring.datasource.url=%s", postgresContainer.getJdbcUrl()),
				String.format("spring.datasource.username=%s", postgresContainer.getUsername()),
				String.format("spring.datasource.password=%s", postgresContainer.getPassword()));

	@Test
	void getSettings_shouldHaveSchemaLocations() {
		this.contextRunner.run(context -> {
			var dataSource = context.getBean(DataSource.class);
			// Use new signature: requires JdbcChatMemoryRepositoryProperties
			var settings = JdbcChatMemoryRepositorySchemaInitializer.getSettings(dataSource,
					new JdbcChatMemoryRepositoryProperties());

			assertThat(settings.getSchemaLocations())
				.containsOnly("classpath:org/springframework/ai/chat/memory/jdbc/schema-postgresql.sql");
		});
	}

}
