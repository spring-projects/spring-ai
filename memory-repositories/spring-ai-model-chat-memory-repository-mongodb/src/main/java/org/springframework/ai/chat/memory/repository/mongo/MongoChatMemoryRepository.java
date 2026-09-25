/*
 * Copyright 2023-present the original author or authors.
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
import java.util.Objects;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

/**
 * An implementation of {@link ChatMemoryRepository} for MongoDB.
 *
 * @author Lukasz Jernas
 * @since 1.1.0
 */
public final class MongoChatMemoryRepository implements ChatMemoryRepository {

	private static final Log logger = LogFactory.getLog(MongoChatMemoryRepository.class);

	private final MongoTemplate mongoTemplate;

	private final TransactionTemplate transactionTemplate;

	private MongoChatMemoryRepository(MongoTemplate mongoTemplate,
			@Nullable PlatformTransactionManager transactionManager) {
		Assert.notNull(mongoTemplate, "mongoTemplate cannot be null");
		this.mongoTemplate = mongoTemplate;
		if (transactionManager == null) {
			MongoDatabaseFactory mongoDatabaseFactory = mongoTemplate.getMongoDatabaseFactory();
			Assert.notNull(mongoDatabaseFactory, "mongoTemplate mongoDatabaseFactory cannot be null");
			transactionManager = new MongoTransactionManager(mongoDatabaseFactory);
		}
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	@Override
	public List<String> findConversationIds() {
		return this.mongoTemplate.query(Conversation.class).distinct("conversationId").as(String.class).all();
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		var messages = this.mongoTemplate.query(Conversation.class)
			.matching(Query.query(Criteria.where("conversationId").is(conversationId))
				.with(Sort.by("timestamp").ascending()));
		return messages.stream().map(MongoChatMemoryRepository::mapMessage).filter(Objects::nonNull).toList();
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		List<Message> persistableMessages = messages.stream()
			.filter(m -> !(m instanceof ToolResponseMessage)
					&& !(m instanceof AssistantMessage am && am.hasToolCalls()))
			.toList();
		if (logger.isWarnEnabled() && persistableMessages.size() < messages.size()) {
			logger.warn(
					"MongoChatMemoryRepository does not support tool call messages. Some messages were filtered out for conversation: "
							+ conversationId);
		}
		this.transactionTemplate.executeWithoutResult(status -> {
			deleteByConversationId(conversationId);
			var conversations = persistableMessages.stream()
				.map(message -> new Conversation(conversationId,
						new Conversation.Message(message.getText(), message.getMessageType().name(),
								message.getMetadata()),
						Instant.now()))
				.toList();
			this.mongoTemplate.insert(conversations, Conversation.class);
		});
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		this.mongoTemplate.remove(Query.query(Criteria.where("conversationId").is(conversationId)), Conversation.class);
	}

	public static @Nullable Message mapMessage(Conversation conversation) {
		final String content = Objects.requireNonNullElse(conversation.message().content(), "");
		return switch (conversation.message().type()) {
			case "USER" -> UserMessage.builder().text(content).metadata(conversation.message().metadata()).build();
			case "ASSISTANT" ->
				AssistantMessage.builder().content(content).properties(conversation.message().metadata()).build();
			case "SYSTEM" -> SystemMessage.builder().text(content).metadata(conversation.message().metadata()).build();
			// this implementation doesn't support tool calls message persistence, so
			// TOOL rows are filtered out by the caller
			case "TOOL" -> null;
			default -> {
				if (logger.isWarnEnabled()) {
					logger.warn("Unsupported message type: " + conversation.message().type());
				}
				throw new IllegalStateException("Unsupported message type: " + conversation.message().type());
			}
		};
	}

	public static Builder builder() {
		return new Builder();
	}

	public final static class Builder {

		private @Nullable MongoTemplate mongoTemplate;

		private @Nullable PlatformTransactionManager transactionManager;

		private Builder() {
		}

		public Builder mongoTemplate(MongoTemplate mongoTemplate) {
			this.mongoTemplate = mongoTemplate;
			return this;
		}

		/**
		 * Optionally provide a {@link PlatformTransactionManager} used to make
		 * {@link #saveAll(String, List)} atomic. When omitted, a
		 * {@link MongoTransactionManager} is created from the {@link MongoTemplate}'s
		 * database factory (MongoDB multi-document transactions require a replica set).
		 * @param transactionManager the transaction manager, or {@code null} to create a
		 * default
		 * @return this builder
		 */
		public Builder transactionManager(@Nullable PlatformTransactionManager transactionManager) {
			this.transactionManager = transactionManager;
			return this;
		}

		public MongoChatMemoryRepository build() {
			Assert.state(this.mongoTemplate != null, "mongoTemplate must be provided");
			return new MongoChatMemoryRepository(this.mongoTemplate, this.transactionManager);
		}

	}

}
