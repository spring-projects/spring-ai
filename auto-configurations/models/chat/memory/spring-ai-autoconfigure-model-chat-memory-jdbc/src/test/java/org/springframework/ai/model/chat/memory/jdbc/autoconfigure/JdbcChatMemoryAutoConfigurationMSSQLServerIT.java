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
import org.springframework.ai.chat.memory.jdbc.JdbcChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Xavier Chopin
 */
@Testcontainers
class JdbcChatMemoryAutoConfigurationMSSQLServerIT {

	static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest");

	@Container
	@SuppressWarnings("resource")
	static MSSQLServerContainer<?> mssqlContainer = new MSSQLServerContainer<>(DEFAULT_IMAGE_NAME)
			.acceptLicense()
			.withEnv("MSSQL_DATABASE", "chat_memory_auto_configuration_test")
			.withPassword("Strong!NotR34LLyPassword");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JdbcChatMemoryAutoConfiguration.class,
				JdbcTemplateAutoConfiguration.class, DataSourceAutoConfiguration.class))
		.withPropertyValues(String.format("spring.datasource.url=%s", mssqlContainer.getJdbcUrl()),
				String.format("spring.datasource.username=%s", mssqlContainer.getUsername()),
				String.format("spring.datasource.password=%s", mssqlContainer.getPassword()));

	@Test
	void jdbcChatMemoryScriptDatabaseInitializer_shouldBeLoaded() {
		this.contextRunner.withPropertyValues("spring.ai.chat.memory.jdbc.initialize-schema=true")
			.run(context -> assertThat(context.containsBean("jdbcChatMemoryScriptDatabaseInitializer")).isTrue());
	}

	@Test
	void jdbcChatMemoryScriptDatabaseInitializer_shouldNotBeLoaded() {
		this.contextRunner.withPropertyValues("spring.ai.chat.memory.jdbc.initialize-schema=false")
			.run(context -> assertThat(context.containsBean("jdbcChatMemoryScriptDatabaseInitializer")).isFalse());
	}

	@Test
	void addGetAndClear_shouldAllExecute() {
		this.contextRunner.withPropertyValues("spring.ai.chat.memory.jdbc.initialize-schema=true").run(context -> {
			var chatMemory = context.getBean(JdbcChatMemory.class);
			var conversationId = UUID.randomUUID().toString();
			var userMessage = new UserMessage("Message from the user");

			chatMemory.add(conversationId, userMessage);

			assertThat(chatMemory.get(conversationId, Integer.MAX_VALUE)).hasSize(1);
			assertThat(chatMemory.get(conversationId, Integer.MAX_VALUE)).isEqualTo(List.of(userMessage));

			chatMemory.clear(conversationId);

			assertThat(chatMemory.get(conversationId, Integer.MAX_VALUE)).isEmpty();

			var multipleMessages = List.<Message>of(new UserMessage("Message from the user 1"),
					new AssistantMessage("Message from the assistant 1"));

			chatMemory.add(conversationId, multipleMessages);

			assertThat(chatMemory.get(conversationId, Integer.MAX_VALUE)).hasSize(multipleMessages.size());
			assertThat(chatMemory.get(conversationId, Integer.MAX_VALUE)).isEqualTo(multipleMessages);
		});
	}

}
