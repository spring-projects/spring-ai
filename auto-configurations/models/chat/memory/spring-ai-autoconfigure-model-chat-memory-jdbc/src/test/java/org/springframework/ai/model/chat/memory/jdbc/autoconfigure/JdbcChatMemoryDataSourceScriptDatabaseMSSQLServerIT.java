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

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Xavier Chopin
 */
@Testcontainers
class JdbcChatMemoryDataSourceScriptDatabaseMSSQLServerIT {

	static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest");

	@Container
	@SuppressWarnings("resource")
	static MSSQLServerContainer<?> mssqlContainer = new MSSQLServerContainer<>(DEFAULT_IMAGE_NAME)
			.acceptLicense()
			.withEnv("MSSQL_DATABASE", "chat_memory_test")
			.withPassword("Strong!NotR34LLyPassword");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JdbcChatMemoryAutoConfiguration.class,
				JdbcTemplateAutoConfiguration.class, DataSourceAutoConfiguration.class))
		.withPropertyValues(String.format("spring.datasource.url=%s", mssqlContainer.getJdbcUrl()),
				String.format("spring.datasource.username=%s", mssqlContainer.getUsername()),
				String.format("spring.datasource.password=%s", mssqlContainer.getPassword()));

	@Test
	void getSettings_shouldHaveSchemaLocations() {
		this.contextRunner.run(context -> {
			var dataSource = context.getBean(DataSource.class);
			var settings = JdbcChatMemoryDataSourceScriptDatabaseInitializer.getSettings(dataSource);

			assertThat(settings.getSchemaLocations())
				.containsOnly("classpath:org/springframework/ai/chat/memory/jdbc/schema-sqlserver.sql");
		});
	}

}
