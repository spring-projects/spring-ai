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

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Jonathan Leijendekker
 * @author Thomas Vitale
 * @author Linar Abzaltdinov
 */
class JdbcChatMemoryPostgresqlAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(JdbcChatMemoryRepositoryAutoConfiguration.class,
				JdbcTemplateAutoConfiguration.class, DataSourceAutoConfiguration.class))
		.withPropertyValues("spring.datasource.url=jdbc:tc:postgresql:17:///");

	@Test
	void jdbcChatMemoryScriptDatabaseInitializer_shouldBeLoaded() {
		this.contextRunner.withPropertyValues("spring.ai.chat.memory.repository.jdbc.initialize-schema=always")
			.run(context -> assertThat(context.containsBean("jdbcChatMemoryScriptDatabaseInitializer")).isTrue());
	}

	@Test
	void jdbcChatMemoryScriptDatabaseInitializer_shouldNotRunSchemaInit() {
		this.contextRunner.withPropertyValues("spring.ai.chat.memory.repository.jdbc.initialize-schema=never")
			.run(context -> {
				assertThat(context.containsBean("jdbcChatMemoryScriptDatabaseInitializer")).isTrue();
				// Optionally, check that the schema is not initialized (could check table
				// absence if needed)
			});
	}

	@Test
	void initializeSchemaEmbeddedDefault() {
		this.contextRunner.withPropertyValues("spring.ai.chat.memory.repository.jdbc.initialize-schema=embedded")
			.run(context -> assertThat(context.containsBean("jdbcChatMemoryScriptDatabaseInitializer")).isTrue());
	}

	@Test
	void useAutoConfiguredJdbcChatMemoryRepository() {
		this.contextRunner.run(context -> {
			var chatMemoryRepository = context.getBean(JdbcChatMemoryRepository.class);
			var conversationId = UUID.randomUUID().toString();
			var userMessage = new UserMessage("Message from the user");

			chatMemoryRepository.saveAll(conversationId, List.of(userMessage));

			assertThat(chatMemoryRepository.findByConversationId(conversationId)).hasSize(1);
			assertThat(chatMemoryRepository.findByConversationId(conversationId)).isEqualTo(List.of(userMessage));

			chatMemoryRepository.deleteByConversationId(conversationId);

			assertThat(chatMemoryRepository.findByConversationId(conversationId)).isEmpty();

			var multipleMessages = List.<Message>of(new UserMessage("Message from the user 1"),
					new AssistantMessage("Message from the assistant 1"));

			chatMemoryRepository.saveAll(conversationId, multipleMessages);

			assertThat(chatMemoryRepository.findByConversationId(conversationId)).hasSize(multipleMessages.size());
			assertThat(chatMemoryRepository.findByConversationId(conversationId)).isEqualTo(multipleMessages);
		});
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
