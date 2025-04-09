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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * An implementation of {@link ChatMemory} for MongoDB. Creating an instance of
 * MongoDbChatMemory example:
 * <code>MongoDbChatMemoryConfig.builder().withTemplate(mongoTemplate).build()</code>
 *
 * @author Łukasz Jernaś
 * @since 1.0.0
 */
public class MongoDbChatMemory implements ChatMemory {

	private final MongoTemplate mongoTemplate;

	private static final Logger logger = LoggerFactory.getLogger(MongoDbChatMemory.class);

	public MongoDbChatMemory(MongoDbChatMemoryConfig config) {
		this.mongoTemplate = config.mongoTemplate;
	}

	public static MongoDbChatMemory create(MongoDbChatMemoryConfig config) {
		return new MongoDbChatMemory(config);
	}

	@Override
	public List<Message> get(String conversationId, int lastN) {
		var messages = mongoTemplate.query(Conversation.class)
			.matching(query(where("conversationId").is(conversationId)).with(Sort.by("timestamp").descending())
				.limit(lastN));
		return messages.stream().map(MongoDbChatMemory::mapMessage).collect(Collectors.toList());
	}

	@Override
	public void clear(String conversationId) {
		mongoTemplate.remove(query(where("conversationId").is(conversationId)), Conversation.class);
	}

	@Override
	public void add(@NonNull String conversationId, @NonNull List<Message> messages) {
		var conversations = messages.stream()
			.map(message -> new Conversation(conversationId,
					new Conversation.Message(message.getText(), message.getMessageType().name()), Instant.now()))
			.toList();
		mongoTemplate.insert(conversations, Conversation.class);
	}

	@Override
	public void add(@NonNull String conversationId, @NonNull Message message) {
		mongoTemplate.insert(new Conversation(conversationId,
				new Conversation.Message(message.getText(), message.getMessageType().name()), Instant.now()));
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

}
