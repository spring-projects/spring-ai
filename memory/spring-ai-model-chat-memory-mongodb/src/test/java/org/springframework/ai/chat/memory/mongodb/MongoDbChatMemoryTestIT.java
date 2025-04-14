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

package org.springframework.ai.chat.memory.mongodb;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class MongoDbChatMemoryTestIT {

	@Container
	@ServiceConnection
	static MongoDBContainer mongoDbContainer = new MongoDBContainer("mongo:8.0.6");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(MongoDbChatMemoryTestIT.TestApplication.class)
			.withPropertyValues(
					String.format("spring.data.mongodb.uri=%s/ai_test", mongoDbContainer.getConnectionString()));

	@Test
	void ensureBeanGetsCreated() {
		this.contextRunner.run(context -> {
			MongoDbChatMemory memory = context.getBean(MongoDbChatMemory.class);
			Assertions.assertNotNull(memory);
		});
	}

	@ParameterizedTest
	@CsvSource({"Message from assistant,ASSISTANT", "Message from user,USER", "Message from system,SYSTEM"})
	void add_shouldInsertSingleMessage(String content, MessageType messageType) {
		this.contextRunner.run(context -> {
			var chatMemory = context.getBean(ChatMemory.class);
			var conversationId = UUID.randomUUID().toString();
			var message = switch (messageType) {
				case ASSISTANT -> new AssistantMessage(content + " - " + conversationId);
				case USER -> new UserMessage(content + " - " + conversationId);
				case SYSTEM -> new SystemMessage(content + " - " + conversationId);
				default -> throw new IllegalArgumentException("Type not supported: " + messageType);
			};
			chatMemory.add(conversationId, message);
			var messages = chatMemory.get(conversationId, 1);
			assertEquals(1, messages.size());
			assertEquals(message, messages.get(0));
		});
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	public static class TestApplication {

		@Bean
		public ChatMemory chatMemory(MongoTemplate mongoTemplate) {
			var config = MongoDbChatMemoryConfig.builder().withTemplate(mongoTemplate).build();
			return MongoDbChatMemory.create(config);
		}

	}

}
