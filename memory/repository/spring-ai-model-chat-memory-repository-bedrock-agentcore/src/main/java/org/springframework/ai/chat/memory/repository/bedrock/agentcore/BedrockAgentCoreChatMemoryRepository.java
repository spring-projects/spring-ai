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

package org.springframework.ai.chat.memory.repository.bedrock.agentcore;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.Content;
import software.amazon.awssdk.services.bedrockagentcore.model.Conversational;
import software.amazon.awssdk.services.bedrockagentcore.model.CreateEventRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.DeleteEventRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.DeleteMemoryRecordRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.Event;
import software.amazon.awssdk.services.bedrockagentcore.model.ListEventsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.ListMemoryRecordsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.ListSessionsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryRecordSummary;
import software.amazon.awssdk.services.bedrockagentcore.model.PayloadType;
import software.amazon.awssdk.services.bedrockagentcore.model.RetrieveMemoryRecordsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;
import software.amazon.awssdk.services.bedrockagentcore.model.SearchCriteria;
import software.amazon.awssdk.services.bedrockagentcore.model.SessionSummary;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.Assert;

/**
 * An implementation of {@link AdvancedBedrockAgentCoreChatMemoryRepository} backed by
 * Amazon Bedrock AgentCore Memory.
 *
 * <p>
 * Maps Spring AI {@link Message} objects to Bedrock AgentCore Events. The
 * {@code conversationId} maps to the Bedrock AgentCore {@code sessionId}. Message roles
 * are stored using the native {@link Role} enum in the {@code Conversational} payload.
 * </p>
 *
 * <p>
 * Use {@link #builder()} or construct directly with a
 * {@link BedrockAgentCoreChatMemoryConfig}.
 * </p>
 *
 * @author Chaemin Lee
 * @see AdvancedBedrockAgentCoreChatMemoryRepository
 * @see BedrockAgentCoreChatMemoryConfig
 */
public final class BedrockAgentCoreChatMemoryRepository implements AdvancedBedrockAgentCoreChatMemoryRepository {

	private static final Logger logger = LoggerFactory.getLogger(BedrockAgentCoreChatMemoryRepository.class);

	static final String METADATA_KEY_EVENT_ID = "bedrockEventId";

	private final BedrockAgentCoreChatMemoryConfig config;

	private final BedrockAgentCoreClient bedrockAgentCoreClient;

	public BedrockAgentCoreChatMemoryRepository(BedrockAgentCoreChatMemoryConfig config) {
		Assert.notNull(config, "config must not be null");

		this.config = config;
		this.bedrockAgentCoreClient = config.getBedrockAgentCoreClient();
	}

	@Override
	public List<String> findConversationIds() {
		return findConversationIds(this.config.getActorId());
	}

	@Override
	public List<String> findConversationIds(String actorId) {
		Assert.hasText(actorId, "actorId must not be empty");

		ListSessionsRequest request = ListSessionsRequest.builder()
			.memoryId(this.config.getMemoryId())
			.actorId(actorId)
			.build();

		try {
			return this.bedrockAgentCoreClient.listSessionsPaginator(request)
				.sessionSummaries()
				.stream()
				.map(SessionSummary::sessionId)
				.toList();
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to list conversation ids", e);
		}
	}

	@Override
	public List<Message> findByConversationId(String conversationId) {
		return findByConversationId(this.config.getActorId(), conversationId);
	}

	@Override
	public List<Message> findByConversationId(String actorId, String conversationId) {
		Assert.hasText(actorId, "actorId must not be empty");
		Assert.hasText(conversationId, "conversationId must not be empty");

		return findEventsByConversationId(actorId, conversationId,
				Comparator.comparing(Event::eventTimestamp, Comparator.nullsLast(Comparator.naturalOrder())))
			.stream()
			.map(BedrockAgentCoreChatMemoryRepository::toMessage)
			.toList();
	}

	@Override
	public void saveAll(String conversationId, List<Message> messages) {
		saveAll(this.config.getActorId(), conversationId, messages);
	}

	@Override
	public void saveAll(String actorId, String conversationId, List<Message> messages) {
		Assert.hasText(actorId, "actorId must not be empty");
		Assert.hasText(conversationId, "conversationId must not be empty");
		Assert.notNull(messages, "messages must not be null");
		Assert.noNullElements(messages, "messages cannot contain null elements");

		deleteByConversationId(actorId, conversationId);
		for (Message message : messages) {
			CreateEventRequest request = CreateEventRequest.builder()
				.memoryId(this.config.getMemoryId())
				.actorId(actorId)
				.sessionId(conversationId)
				.eventTimestamp(Instant.now())
				.payload(toPayload(message))
				.build();
			try {
				this.bedrockAgentCoreClient.createEvent(request);
			}
			catch (Exception e) {
				throw new IllegalStateException(
						"Failed to save event for actorId=" + actorId + ", conversationId=" + conversationId, e);
			}
		}
	}

	@Override
	public void deleteByConversationId(String conversationId) {
		deleteByConversationId(this.config.getActorId(), conversationId);
	}

	@Override
	public void deleteByConversationId(String actorId, String conversationId) {
		Assert.hasText(actorId, "actorId must not be empty");
		Assert.hasText(conversationId, "conversationId must not be empty");

		List<Event> events = findEventsByConversationId(actorId, conversationId, null);
		for (Event event : events) {
			DeleteEventRequest request = DeleteEventRequest.builder()
				.memoryId(this.config.getMemoryId())
				.actorId(actorId)
				.sessionId(conversationId)
				.eventId(event.eventId())
				.build();
			try {
				this.bedrockAgentCoreClient.deleteEvent(request);
			}
			catch (Exception e) {
				throw new IllegalStateException(
						"Failed to delete event for actorId=" + actorId + ", conversationId=" + conversationId, e);
			}
		}
	}

	private List<Event> findEventsByConversationId(String actorId, String conversationId,
			@Nullable Comparator<Event> comparator) {
		ListEventsRequest request = ListEventsRequest.builder()
			.memoryId(this.config.getMemoryId())
			.actorId(actorId)
			.sessionId(conversationId)
			.includePayloads(true)
			.build();

		try {
			Stream<Event> stream = this.bedrockAgentCoreClient.listEventsPaginator(request).events().stream();
			if (comparator != null) {
				stream = stream.sorted(comparator);
			}
			return stream.toList();
		}
		catch (Exception e) {
			throw new IllegalStateException(
					"Failed to list events for actorId=" + actorId + ", conversationId=" + conversationId, e);
		}
	}

	private static PayloadType toPayload(Message message) {
		Role role = switch (message.getMessageType()) {
			case ASSISTANT -> Role.ASSISTANT;
			case TOOL -> Role.TOOL;
			case SYSTEM -> Role.OTHER;
			default -> Role.USER;
		};
		// ToolResponseMessage stores its data in getResponses(), not getText().
		// Bedrock AgentCore's Conversational payload is text-only, so ToolResponse
		// entries (id, name, responseData) cannot be persisted and are intentionally
		// discarded here (see toMessage for read-back behavior).
		return PayloadType.fromConversational(
				Conversational.builder().role(role).content(Content.builder().text(message.getText()).build()).build());
	}

	private static Message toMessage(Event event) {
		String text = "";
		Role role = Role.USER;
		if (event.hasPayload()) {
			for (PayloadType payload : event.payload()) {
				Conversational conv = payload.conversational();
				if (conv != null) {
					Content content = conv.content();
					if (content != null && content.text() != null) {
						text = content.text();
					}
					if (conv.role() != null) {
						role = conv.role();
					}
					break;
				}
			}
		}
		Map<String, Object> metadata = new HashMap<>();
		metadata.put(METADATA_KEY_EVENT_ID, event.eventId());
		return switch (role) {
			case ASSISTANT -> AssistantMessage.builder().content(text).properties(metadata).build();
			case USER -> UserMessage.builder().text(text).metadata(metadata).build();
			// Conversational events do not carry ToolResponse entries; use an empty list
			// because
			// ToolResponseMessage data lives in getResponses(), which was not persisted
			// (toPayload).
			case TOOL -> ToolResponseMessage.builder().responses(List.of()).metadata(metadata).build();
			case OTHER -> SystemMessage.builder().text(text).metadata(metadata).build();
			default -> {
				logger.warn("Unknown role '{}', defaulting to UserMessage", role);
				yield UserMessage.builder().text(text).metadata(metadata).build();
			}
		};
	}

	@Override
	public List<MemoryRecordSummary> retrieveMemoryRecords(String namespace, String searchQuery) {
		Assert.hasText(namespace, "namespace must not be empty");
		Assert.hasText(searchQuery, "searchQuery must not be empty");

		RetrieveMemoryRecordsRequest request = RetrieveMemoryRecordsRequest.builder()
			.memoryId(this.config.getMemoryId())
			.namespace(namespace)
			.searchCriteria(SearchCriteria.builder().searchQuery(searchQuery).build())
			.build();

		try {
			return this.bedrockAgentCoreClient.retrieveMemoryRecordsPaginator(request)
				.memoryRecordSummaries()
				.stream()
				.toList();
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to retrieve memory records for namespace=" + namespace, e);
		}
	}

	@Override
	public List<MemoryRecordSummary> listMemoryRecords(String namespace) {
		Assert.hasText(namespace, "namespace must not be empty");

		ListMemoryRecordsRequest request = ListMemoryRecordsRequest.builder()
			.memoryId(this.config.getMemoryId())
			.namespace(namespace)
			.build();

		try {
			return this.bedrockAgentCoreClient.listMemoryRecordsPaginator(request)
				.memoryRecordSummaries()
				.stream()
				.toList();
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to list memory records for namespace=" + namespace, e);
		}
	}

	@Override
	public void deleteMemoryRecord(String memoryRecordId) {
		Assert.hasText(memoryRecordId, "memoryRecordId must not be empty");

		DeleteMemoryRecordRequest request = DeleteMemoryRecordRequest.builder()
			.memoryId(this.config.getMemoryId())
			.memoryRecordId(memoryRecordId)
			.build();
		try {
			this.bedrockAgentCoreClient.deleteMemoryRecord(request);
		}
		catch (Exception e) {
			throw new IllegalStateException("Failed to delete memory record: " + memoryRecordId, e);
		}
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link BedrockAgentCoreChatMemoryRepository}.
	 */
	public static final class Builder {

		private @Nullable BedrockAgentCoreClient bedrockAgentCoreClient;

		private @Nullable String memoryId;

		private String actorId = BedrockAgentCoreChatMemoryConfig.DEFAULT_ACTOR_ID;

		private Builder() {
		}

		public Builder bedrockAgentCoreClient(BedrockAgentCoreClient bedrockAgentCoreClient) {
			this.bedrockAgentCoreClient = bedrockAgentCoreClient;
			return this;
		}

		public Builder memoryId(String memoryId) {
			this.memoryId = memoryId;
			return this;
		}

		public Builder actorId(String actorId) {
			this.actorId = actorId;
			return this;
		}

		public BedrockAgentCoreChatMemoryRepository build() {
			Assert.notNull(this.bedrockAgentCoreClient, "bedrockAgentCoreClient must not be null");
			Assert.hasText(this.memoryId, "memoryId must not be empty");
			Assert.hasText(this.actorId, "actorId must not be empty");

			BedrockAgentCoreChatMemoryConfig config = BedrockAgentCoreChatMemoryConfig.builder()
				.bedrockAgentCoreClient(this.bedrockAgentCoreClient)
				.memoryId(this.memoryId)
				.actorId(this.actorId)
				.build();
			return new BedrockAgentCoreChatMemoryRepository(config);
		}

	}

}
