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

package org.springframework.ai.model.chat.memory.repository.mongo.autoconfigure;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.mongo.Conversation;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = { "spring.ai.chat.memory.repository.mongo.create-indices=true" })
class MongoChatMemoryAutoConfigurationIT {

	@Autowired
	private ChatMemoryRepository chatMemoryRepository;

	@Autowired
	private MongoTemplate mongoTemplate;

	@Container
	@ServiceConnection
	static MongoDBContainer mongoDbContainer = new MongoDBContainer("mongo:8.0.6");

	@Test
	void allMethodsShouldExecute() {
		var conversationId = UUID.randomUUID().toString();
		var systemMessage = new SystemMessage("Some system message");

		this.chatMemoryRepository.saveAll(conversationId, List.of(systemMessage));

		assertThat(this.chatMemoryRepository.findConversationIds().contains(conversationId)).isTrue();

		assertThat(this.chatMemoryRepository.findByConversationId(conversationId).size()).isEqualTo(1);

		this.chatMemoryRepository.deleteByConversationId(conversationId);

		assertThat(this.chatMemoryRepository.findByConversationId(conversationId).size()).isEqualTo(0);

	}

	@Test
	void indicesShouldBeCreated() {
		var conversationId = UUID.randomUUID().toString();
		var systemMessage = new SystemMessage("Some system message");

		this.chatMemoryRepository.saveAll(conversationId, List.of(systemMessage));

		assertThat(this.mongoTemplate.indexOps(Conversation.class).getIndexInfo().size()).isEqualTo(2);
	}

	@Configuration
	@EnableAutoConfiguration
	static class TestConfiguration {

	}

}
