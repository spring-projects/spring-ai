/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.chat.memory.repository.mongo;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MongoChatMemoryRepository}.
 *
 * @author Łukasz Jernaś
 */
@SpringBootTest
public class MongoChatMemoryRepositoryIT {

	@Autowired
	private ChatMemoryRepository chatMemoryRepository;

	@Autowired
	private MongoTemplate mongoTemplate;

	@Container
	@ServiceConnection
	static MongoDBContainer mongoDbContainer = new MongoDBContainer("mongo:8.0.6");

	@Test
	void correctChatMemoryRepositoryInstance() {
		assertThat(this.chatMemoryRepository).isInstanceOf(ChatMemoryRepository.class);
	}

	@ParameterizedTest
	@CsvSource({ "Message from assistant,ASSISTANT", "Message from user,USER", "Message from system,SYSTEM" })
	void saveMessagesSingleMessage(String content, MessageType messageType) {
		var conversationId = UUID.randomUUID().toString();
		var message = switch (messageType) {
			case ASSISTANT -> new AssistantMessage(content + " - " + conversationId);
			case USER -> new UserMessage(content + " - " + conversationId);
			case SYSTEM -> new SystemMessage(content + " - " + conversationId);
			default -> throw new IllegalArgumentException("Type not supported: " + messageType);
		};

		this.chatMemoryRepository.saveAll(conversationId, List.of(message));

		var result = this.mongoTemplate.query(Conversation.class)
			.matching(Query.query(Criteria.where("conversationId").is(conversationId)))
			.first();

		assertThat(result.isPresent()).isTrue();

		assertThat(result.stream().count()).isEqualTo(1);
		assertThat(result.get().conversationId()).isEqualTo(conversationId);
		assertThat(result.get().message().content()).isEqualTo(message.getText());
		assertThat(result.get().message().type()).isEqualTo(messageType.toString());
		assertThat(result.get().timestamp()).isNotNull();
	}

	@Test
	void saveMultipleMessages() {
		var conversationId = UUID.randomUUID().toString();
		var messages = List.<Message>of(new AssistantMessage("Message from assistant - " + conversationId),
				new UserMessage("Message from user - " + conversationId),
				new SystemMessage("Message from system - " + conversationId));

		this.chatMemoryRepository.saveAll(conversationId, messages);

		var result = this.mongoTemplate.query(Conversation.class)
			.matching(Query.query(Criteria.where("conversationId").is(conversationId)))
			.all();

		assertThat(result.size()).isEqualTo(messages.size());

	}

	@Test
	void findByConversationId() {
		var conversationId = UUID.randomUUID().toString();
		var messages = List.<Message>of(new AssistantMessage("Message from assistant - " + conversationId),
				new UserMessage("Message from user - " + conversationId),
				new SystemMessage("Message from system - " + conversationId));

		this.chatMemoryRepository.saveAll(conversationId, messages);

		var results = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(results.size()).isEqualTo(messages.size());
		assertThat(results).isEqualTo(messages);
	}

	@Test
	void messagesAreReturnedInChronologicalOrder() {
		var conversationId = UUID.randomUUID().toString();
		var messages = List.<Message>of(new UserMessage("First message"), new AssistantMessage("Second message"),
				new UserMessage("Third message"));

		this.chatMemoryRepository.saveAll(conversationId, messages);

		var results = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(results).isEqualTo(messages);
	}

	@Test
	void deleteMessagesByConversationId() {
		var conversationId = UUID.randomUUID().toString();
		var messages = List.<Message>of(new AssistantMessage("Message from assistant - " + conversationId),
				new UserMessage("Message from user - " + conversationId),
				new SystemMessage("Message from system - " + conversationId));

		this.chatMemoryRepository.saveAll(conversationId, messages);

		this.chatMemoryRepository.deleteByConversationId(conversationId);

		var results = this.mongoTemplate.query(Conversation.class)
			.matching(Query.query(Criteria.where("conversationId").is(conversationId)))
			.all();

		assertThat(results.size()).isZero();
	}

	@SpringBootConfiguration
	@ImportAutoConfiguration({ MongoAutoConfiguration.class, DataMongoAutoConfiguration.class })
	static class TestConfiguration {

		@Bean
		ChatMemoryRepository chatMemoryRepository(MongoTemplate mongoTemplate) {
			return MongoChatMemoryRepository.builder().mongoTemplate(mongoTemplate).build();
		}

	}

}
