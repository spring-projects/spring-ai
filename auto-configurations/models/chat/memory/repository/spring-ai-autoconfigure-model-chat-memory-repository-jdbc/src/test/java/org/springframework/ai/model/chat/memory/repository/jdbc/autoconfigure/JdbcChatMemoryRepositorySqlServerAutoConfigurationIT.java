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

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Integration test for SQL Server using Testcontainers, following the same structure as the PostgreSQL test.
 */
@Testcontainers
class JdbcChatMemoryRepositorySqlServerAutoConfigurationIT {

	static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName
		.parse("mcr.microsoft.com/mssql/server:2022-latest");

	@Container
	@SuppressWarnings("resource")
	static MSSQLServerContainer<?> mssqlContainer = new MSSQLServerContainer<>(DEFAULT_IMAGE_NAME).acceptLicense()
		.withEnv("MSSQL_DATABASE", "chat_memory_auto_configuration_test")
		.withPassword("Strong!NotR34LLyPassword")
		.withUrlParam("loginTimeout", "60") // Give more time for the login
		.withUrlParam("connectRetryCount", "10") // Retry 10 times
		.withUrlParam("connectRetryInterval", "10")
		.withStartupTimeout(Duration.ofSeconds(60));

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JdbcChatMemoryRepositoryAutoConfiguration.class,
				JdbcTemplateAutoConfiguration.class, DataSourceAutoConfiguration.class))
		.withPropertyValues(String.format("spring.datasource.url=%s", mssqlContainer.getJdbcUrl()),
				String.format("spring.datasource.username=%s", mssqlContainer.getUsername()),
				String.format("spring.datasource.password=%s", mssqlContainer.getPassword()));

	@Test
	void jdbcChatMemoryScriptDatabaseInitializer_shouldBeLoaded() {
		this.contextRunner.withPropertyValues("spring.ai.chat.memory.repository.jdbc.initialize-schema=always")
			.run(context -> assertThat(context).hasBean("jdbcChatMemoryScriptDatabaseInitializer"));
	}

	@Test
	void jdbcChatMemoryScriptDatabaseInitializer_shouldNotRunSchemaInit() {
		this.contextRunner.withPropertyValues("spring.ai.chat.memory.repository.jdbc.initialize-schema=never")
			.run(context -> assertThat(context).doesNotHaveBean("jdbcChatMemoryScriptDatabaseInitializer"));
	}

	@Test
	void initializeSchemaEmbeddedDefault() {
		this.contextRunner.withPropertyValues("spring.ai.chat.memory.repository.jdbc.initialize-schema=embedded")
			.run(context -> assertThat(context).hasBean("jdbcChatMemoryScriptDatabaseInitializer"));
	}

	@Test
	void useAutoConfiguredChatMemoryWithJdbc() {
		this.contextRunner.withConfiguration(AutoConfigurations.of(ChatMemoryAutoConfiguration.class))
			.withPropertyValues("spring.ai.chat.memory.repository.jdbc.initialize-schema=always")
			.run(context -> {
				assertThat(context).hasSingleBean(ChatMemory.class);
				assertThat(context).hasSingleBean(JdbcChatMemoryRepository.class);

				var chatMemory = context.getBean(ChatMemory.class);
				var conversationId = UUID.randomUUID().toString();
				var userMessage = new UserMessage("Message from the user");

				chatMemory.add(conversationId, userMessage);

				assertThat(chatMemory.get(conversationId)).hasSize(1);
				assertThat(chatMemory.get(conversationId)).isEqualTo(List.of(userMessage));

				var assistantMessage = new AssistantMessage("Message from the assistant");

				chatMemory.add(conversationId, List.of(assistantMessage));

				assertThat(chatMemory.get(conversationId)).hasSize(2);
				assertThat(chatMemory.get(conversationId)).isEqualTo(List.of(userMessage, assistantMessage));

				chatMemory.clear(conversationId);

				assertThat(chatMemory.get(conversationId)).isEmpty();

				var multipleMessages = List.<Message>of(new UserMessage("Message from the user 1"),
						new AssistantMessage("Message from the assistant 1"));

				chatMemory.add(conversationId, multipleMessages);

				assertThat(chatMemory.get(conversationId)).hasSize(multipleMessages.size());
				assertThat(chatMemory.get(conversationId)).isEqualTo(multipleMessages);
			});
	}

}
