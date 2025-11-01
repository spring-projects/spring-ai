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

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

/**
 * An implementation of {@link ChatMemoryRepository} for MongoDB.
 *
 * @author Lukasz Jernas
 * @since 1.1.0
 */
public class MongoChatMemoryRepository implements ChatMemoryRepository {

	private static final Logger logger = LoggerFactory.getLogger(MongoChatMemoryRepository.class);

	private final MongoTemplate mongoTemplate;

	public MongoChatMemoryRepository(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public List<String> findConversationIds() {
		return this.mongoTemplate.query(Conversation.class).distinct("conversationId").as(String.class).all();
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		var messages = this.mongoTemplate.query(Conversation.class)
			.matching(Query.query(Criteria.where("conversationId").is(conversationId))
				.with(Sort.by("timestamp").descending()));
		return messages.stream().map(MongoChatMemoryRepository::mapMessage).collect(Collectors.toList());
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		deleteByConversationId(conversationId);
		var conversations = messages.stream()
			.map(message -> new Conversation(conversationId,
					new Conversation.Message(message.getText(), message.getMessageType().name(), message.getMetadata()),
					Instant.now()))
			.toList();
		this.mongoTemplate.insert(conversations, Conversation.class);

	}

	@Override
	public void deleteByConversationId(String conversationId) {
		this.mongoTemplate.remove(Query.query(Criteria.where("conversationId").is(conversationId)), Conversation.class);
	}

	public static @Nullable Message mapMessage(Conversation conversation) {
		return switch (conversation.message().type()) {
			case "USER" -> new UserMessage(conversation.message().content());
			case "ASSISTANT" -> new AssistantMessage(conversation.message().content());
			case "SYSTEM" -> new SystemMessage(conversation.message().content());
			default -> {
				logger.warn("Unsupported message type: {}", conversation.message().type());
				yield null;
			}
		};
	}

	public static Builder builder() {
		return new Builder();
	}

	public final static class Builder {

		private MongoTemplate mongoTemplate;

		private Builder() {
		}

		public Builder mongoTemplate(MongoTemplate mongoTemplate) {
			this.mongoTemplate = mongoTemplate;
			return this;
		}

		public MongoChatMemoryRepository build() {
			return new MongoChatMemoryRepository(this.mongoTemplate);
		}

	}

}
