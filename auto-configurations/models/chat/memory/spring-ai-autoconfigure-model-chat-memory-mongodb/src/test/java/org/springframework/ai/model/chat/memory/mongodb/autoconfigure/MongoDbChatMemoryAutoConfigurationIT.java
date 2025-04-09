/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.model.chat.memory.mongodb.autoconfigure;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.mongodb.MongoDbChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MongoDbChatMemoryAutoConfigurationIT {

	@Container
	@ServiceConnection
	static MongoDBContainer mongoDbContainer = new MongoDBContainer("mongo");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(MongoDbChatMemoryAutoConfiguration.class, MongoAutoConfiguration.class,
				MongoDataAutoConfiguration.class, MongoDbChatMemoryIndexCreator.class))
		.withPropertyValues(String.format("spring.data.mongodb.uri=%s/ai_test", mongoDbContainer.getConnectionString()),
				"spring.ai.chat.memory.mongodb.create-indexes=true", "spring.ai.chat.memory.mongodb.ttl=PT1M");

	@Test
	void mongodbChatMemoryBean_exists() {
		this.contextRunner.run(context -> assertThat(context.containsBean("chatMemory")).isTrue());
	}

	@Test
	void allMethods_shouldExecute() {
		this.contextRunner.run(context -> {
			var chatMemory = context.getBean(MongoDbChatMemory.class);
			var conversationId = UUID.randomUUID().toString();
			var systemMessage = new SystemMessage("Some system message");

			chatMemory.add(conversationId, systemMessage);

			assertThat(chatMemory.get(conversationId, Integer.MAX_VALUE)).hasSize(1);
			assertThat(chatMemory.get(conversationId, Integer.MAX_VALUE)).isEqualTo(List.of(systemMessage));

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
