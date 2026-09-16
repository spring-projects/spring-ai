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
import java.util.ArrayList;
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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.util.Assert;

/**
 * An implementation of {@link ChatMemoryRepository} for MongoDB.
 *
 * @author Lukasz Jernas
 * @author kezhenxu94
 * @since 1.1.0
 */
public final class MongoChatMemoryRepository implements ChatMemoryRepository {

	private static final Log logger = LogFactory.getLog(MongoChatMemoryRepository.class);

	private final MongoTemplate mongoTemplate;

	private MongoChatMemoryRepository(MongoTemplate mongoTemplate) {
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
				.with(Sort.by("timestamp").ascending().and(Sort.by("sequenceId").ascending())));
		return messages.stream().map(MongoChatMemoryRepository::mapMessage).filter(Objects::nonNull).toList();
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		deleteByConversationId(conversationId);
		// A single timestamp is used for the whole batch: BSON dates only have
		// millisecond precision, so the sequence identifier is what orders the messages
		// within a conversation.
		var timestamp = Instant.now();
		var conversations = new ArrayList<Conversation>(messages.size());
		for (int i = 0; i < messages.size(); i++) {
			Message message = messages.get(i);
			var toolCalls = message instanceof AssistantMessage assistantMessage ? assistantMessage.getToolCalls()
					: List.<AssistantMessage.ToolCall>of();
			var toolResponses = message instanceof ToolResponseMessage toolResponseMessage
					? toolResponseMessage.getResponses() : List.<ToolResponseMessage.ToolResponse>of();
			conversations.add(new Conversation(conversationId, new Conversation.Message(message.getText(),
					message.getMessageType().name(), message.getMetadata(), toolCalls, toolResponses), timestamp, i));
		}
		this.mongoTemplate.insert(conversations, Conversation.class);
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		this.mongoTemplate.remove(Query.query(Criteria.where("conversationId").is(conversationId)), Conversation.class);
	}

	public static @Nullable Message mapMessage(Conversation conversation) {
		final Conversation.Message message = conversation.message();
		// USER and SYSTEM messages require non-null text content.
		final String content = Objects.requireNonNullElse(message.content(), "");
		return switch (message.type()) {
			case "USER" -> UserMessage.builder().text(content).metadata(message.metadata()).build();
			case "ASSISTANT" -> AssistantMessage.builder()
				// Assistant messages carrying only tool calls have no text content, and
				// null is preserved so that they round-trip unchanged.
				.content(message.content())
				.properties(message.metadata())
				.toolCalls(Objects.requireNonNullElse(message.toolCalls(), List.of()))
				.build();
			case "SYSTEM" -> SystemMessage.builder().text(content).metadata(message.metadata()).build();
			case "TOOL" -> {
				var toolResponses = message.toolResponses();
				if (toolResponses == null) {
					// Documents written before tool call persistence was supported
					// have no tool response field at all. They would be read back
					// as empty stubs, so they are skipped to avoid polluting the
					// LLM conversation context. A tool message that was genuinely
					// saved without responses stores an empty array instead, and is
					// preserved.
					if (logger.isWarnEnabled()) {
						logger.warn("Skipping tool message without tool responses for conversation: "
								+ conversation.conversationId());
					}
					yield null;
				}
				yield ToolResponseMessage.builder().responses(toolResponses).metadata(message.metadata()).build();
			}
			default -> {
				if (logger.isWarnEnabled()) {
					logger.warn("Unsupported message type: " + message.type());
				}
				throw new IllegalStateException("Unsupported message type: " + message.type());
			}
		};
	}

	public static Builder builder() {
		return new Builder();
	}

	public final static class Builder {

		private @Nullable MongoTemplate mongoTemplate;

		private Builder() {
		}

		public Builder mongoTemplate(MongoTemplate mongoTemplate) {
			this.mongoTemplate = mongoTemplate;
			return this;
		}

		public MongoChatMemoryRepository build() {
			Assert.state(this.mongoTemplate != null, "mongoTemplate must be provided");
			return new MongoChatMemoryRepository(this.mongoTemplate);
		}

	}

}
