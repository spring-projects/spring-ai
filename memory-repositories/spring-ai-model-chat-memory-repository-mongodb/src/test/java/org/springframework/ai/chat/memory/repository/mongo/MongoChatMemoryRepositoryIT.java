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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import org.bson.Document;
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
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link MongoChatMemoryRepository}.
 *
 * @author Łukasz Jernaś
 * @author kezhenxu94
 */
@SpringBootTest(classes = MongoChatMemoryRepositoryIT.TestConfiguration.class)
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
	void toolResponseMessagesAreRoundTripped() {
		var conversationId = UUID.randomUUID().toString();
		var responses = List.of(new ToolResponseMessage.ToolResponse("id1", "myTool", "result"),
				new ToolResponseMessage.ToolResponse("id2", "myOtherTool", "otherResult"),
				new ToolResponseMessage.ToolResponse("id3", "aThirdTool", "yetAnotherResult"));
		var toolResponse = ToolResponseMessage.builder().responses(responses).build();

		this.chatMemoryRepository.saveAll(conversationId, List.of(toolResponse));

		var results = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(results).hasSize(1);
		assertThat(results.get(0)).isInstanceOf(ToolResponseMessage.class);
		assertThat(results.get(0).getMessageType()).isEqualTo(MessageType.TOOL);
		assertThat(((ToolResponseMessage) results.get(0)).getResponses()).containsExactlyElementsOf(responses);
	}

	@Test
	void assistantMessagesWithToolCallsAreRoundTripped() {
		var conversationId = UUID.randomUUID().toString();
		var toolCalls = List.of(
				new AssistantMessage.ToolCall("call1", "function", "getWeather", "{\"city\":\"Paris\"}"),
				new AssistantMessage.ToolCall("call2", "function", "getTime", "{}"));
		var toolCallAssistant = AssistantMessage.builder().toolCalls(toolCalls).build();

		this.chatMemoryRepository.saveAll(conversationId, List.of(toolCallAssistant));

		var results = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(results).hasSize(1);
		assertThat(results.get(0)).isInstanceOf(AssistantMessage.class);
		assertThat(results.get(0).getText()).isNull();
		assertThat(((AssistantMessage) results.get(0)).getToolCalls()).containsExactlyElementsOf(toolCalls);
	}

	@Test
	void toolCallOrderIsPreservedWithinAnAssistantMessage() {
		var conversationId = UUID.randomUUID().toString();
		var toolCalls = IntStream.range(0, 10)
			.mapToObj(i -> new AssistantMessage.ToolCall("call" + i, "function", "tool" + i, "{\"index\":" + i + "}"))
			.toList();

		this.chatMemoryRepository.saveAll(conversationId,
				List.of(AssistantMessage.builder().toolCalls(toolCalls).build()));

		var results = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(((AssistantMessage) results.get(0)).getToolCalls()).containsExactlyElementsOf(toolCalls);
	}

	@Test
	void toolCallArgumentsWithJsonAndSpecialCharactersAreRoundTripped() {
		var conversationId = UUID.randomUUID().toString();
		var arguments = "{\"query\":\"a \\\"quoted\\\" value\",\"nested\":{\"a.b\":1},\"unicode\":\"héllo 世界 🌍\"}";
		var toolCall = new AssistantMessage.ToolCall("call-1", "function", "search", arguments);

		this.chatMemoryRepository.saveAll(conversationId,
				List.of(AssistantMessage.builder().toolCalls(List.of(toolCall)).build()));

		var results = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(((AssistantMessage) results.get(0)).getToolCalls()).containsExactly(toolCall);
	}

	@Test
	void toolResponseDataWithSpecialCharactersIsRoundTripped() {
		var conversationId = UUID.randomUUID().toString();
		var response = new ToolResponseMessage.ToolResponse("call-1", "search",
				"line one\nline two\ttabbed \"quoted\" héllo 世界 🌍");

		this.chatMemoryRepository.saveAll(conversationId,
				List.of(ToolResponseMessage.builder().responses(List.of(response)).build()));

		var results = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(((ToolResponseMessage) results.get(0)).getResponses()).containsExactly(response);
	}

	@Test
	void assistantMessagesWithoutToolCallsAreReadBackWithAnEmptyToolCallList() {
		var conversationId = UUID.randomUUID().toString();

		this.chatMemoryRepository.saveAll(conversationId, List.of(new AssistantMessage("Just text.")));

		var results = this.chatMemoryRepository.findByConversationId(conversationId);
		var assistantMessage = (AssistantMessage) results.get(0);
		assertThat(assistantMessage.getToolCalls()).isEmpty();
		assertThat(assistantMessage.hasToolCalls()).isFalse();
	}

	@Test
	void metadataIsPreservedOnToolCallAndToolResponseMessages() {
		var conversationId = UUID.randomUUID().toString();
		var assistant = AssistantMessage.builder()
			.properties(Map.of("finishReason", "TOOL_CALLS", "attempt", 2))
			.toolCalls(List.of(new AssistantMessage.ToolCall("call1", "function", "getWeather", "{}")))
			.build();
		var toolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("call1", "getWeather", "sunny")))
			.metadata(Map.of("durationMs", 42))
			.build();

		this.chatMemoryRepository.saveAll(conversationId, List.of(assistant, toolResponse));

		var results = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(results.get(0).getMetadata()).containsEntry("finishReason", "TOOL_CALLS")
			.containsEntry("attempt", 2);
		assertThat(results.get(1).getMetadata()).containsEntry("durationMs", 42);
	}

	@Test
	void fullToolCallingTurnIsRoundTrippedInOrder() {
		var conversationId = UUID.randomUUID().toString();
		var messages = List.<Message>of(new UserMessage("What is the weather?"),
				AssistantMessage.builder()
					.toolCalls(List.of(new AssistantMessage.ToolCall("call1", "function", "getWeather", "{}")))
					.build(),
				ToolResponseMessage.builder()
					.responses(List.of(new ToolResponseMessage.ToolResponse("call1", "getWeather", "sunny")))
					.build(),
				new AssistantMessage("It is sunny."));

		this.chatMemoryRepository.saveAll(conversationId, messages);

		assertThat(this.chatMemoryRepository.findByConversationId(conversationId)).isEqualTo(messages);
	}

	@Test
	void parallelToolCallsAndTheirResponsesAreRoundTrippedInOrder() {
		var conversationId = UUID.randomUUID().toString();
		var messages = List.<Message>of(new UserMessage("Weather and time in Paris?"), AssistantMessage.builder()
			.toolCalls(List.of(new AssistantMessage.ToolCall("call1", "function", "getWeather", "{\"city\":\"Paris\"}"),
					new AssistantMessage.ToolCall("call2", "function", "getTime", "{\"city\":\"Paris\"}")))
			.build(),
				ToolResponseMessage.builder()
					.responses(List.of(new ToolResponseMessage.ToolResponse("call1", "getWeather", "sunny"),
							new ToolResponseMessage.ToolResponse("call2", "getTime", "14:00")))
					.build(),
				new AssistantMessage("It is sunny and 14:00 in Paris."));

		this.chatMemoryRepository.saveAll(conversationId, messages);

		assertThat(this.chatMemoryRepository.findByConversationId(conversationId)).isEqualTo(messages);
	}

	@Test
	void multiTurnToolCallingConversationIsRoundTrippedInOrder() {
		var conversationId = UUID.randomUUID().toString();
		var messages = List.<Message>of(new SystemMessage("You are a helpful assistant."),
				new UserMessage("Weather in Paris?"),
				AssistantMessage.builder()
					.toolCalls(List.of(new AssistantMessage.ToolCall("call1", "function", "getWeather", "{}")))
					.build(),
				ToolResponseMessage.builder()
					.responses(List.of(new ToolResponseMessage.ToolResponse("call1", "getWeather", "sunny")))
					.build(),
				new AssistantMessage("It is sunny."), new UserMessage("And in Berlin?"),
				AssistantMessage.builder()
					.toolCalls(List.of(new AssistantMessage.ToolCall("call2", "function", "getWeather", "{}")))
					.build(),
				ToolResponseMessage.builder()
					.responses(List.of(new ToolResponseMessage.ToolResponse("call2", "getWeather", "rainy")))
					.build(),
				new AssistantMessage("It is rainy."));

		this.chatMemoryRepository.saveAll(conversationId, messages);

		assertThat(this.chatMemoryRepository.findByConversationId(conversationId)).isEqualTo(messages);
	}

	@Test
	void savingAgainReplacesThePreviousToolCallingConversation() {
		var conversationId = UUID.randomUUID().toString();
		var firstTurn = List.<Message>of(new UserMessage("Weather in Paris?"),
				AssistantMessage.builder()
					.toolCalls(List.of(new AssistantMessage.ToolCall("call1", "function", "getWeather", "{}")))
					.build());
		this.chatMemoryRepository.saveAll(conversationId, firstTurn);

		var secondTurn = List.<Message>of(new UserMessage("Weather in Paris?"),
				AssistantMessage.builder()
					.toolCalls(List.of(new AssistantMessage.ToolCall("call1", "function", "getWeather", "{}")))
					.build(),
				ToolResponseMessage.builder()
					.responses(List.of(new ToolResponseMessage.ToolResponse("call1", "getWeather", "sunny")))
					.build());
		this.chatMemoryRepository.saveAll(conversationId, secondTurn);

		assertThat(this.chatMemoryRepository.findByConversationId(conversationId)).isEqualTo(secondTurn);
		assertThat(this.mongoTemplate.query(Conversation.class)
			.matching(Query.query(Criteria.where("conversationId").is(conversationId)))
			.all()).hasSize(secondTurn.size());
	}

	@Test
	void toolCallsArePersistedAsOrderedSubdocuments() {
		var conversationId = UUID.randomUUID().toString();
		var toolCall = new AssistantMessage.ToolCall("call1", "function", "getWeather", "{\"city\":\"Paris\"}");

		this.chatMemoryRepository.saveAll(conversationId,
				List.of(AssistantMessage.builder().toolCalls(List.of(toolCall)).build()));

		var stored = this.mongoTemplate.query(Conversation.class)
			.matching(Query.query(Criteria.where("conversationId").is(conversationId)))
			.firstValue();
		assertThat(stored).isNotNull();
		assertThat(stored.message().toolCalls()).containsExactly(toolCall);
		assertThat(stored.message().toolResponses()).isEmpty();
		assertThat(stored.sequenceId()).isZero();
	}

	@Test
	void sequenceIdentifiersAreAssignedInMessageOrder() {
		var conversationId = UUID.randomUUID().toString();
		var messages = List.<Message>of(new UserMessage("One"), new AssistantMessage("Two"), new UserMessage("Three"));

		this.chatMemoryRepository.saveAll(conversationId, messages);

		var stored = this.mongoTemplate.query(Conversation.class)
			.matching(Query.query(Criteria.where("conversationId").is(conversationId))
				.with(Sort.by("sequenceId").ascending()))
			.all();
		assertThat(stored).extracting(Conversation::sequenceId).containsExactly(0, 1, 2);
		// Every message in a batch shares one timestamp, so the sequence identifier is
		// what makes the ordering deterministic.
		assertThat(stored).extracting(Conversation::timestamp).containsOnly(stored.get(0).timestamp());
	}

	@Test
	void conversationsWithToolCallsAreIsolatedFromEachOther() {
		var firstConversationId = UUID.randomUUID().toString();
		var secondConversationId = UUID.randomUUID().toString();
		this.chatMemoryRepository.saveAll(firstConversationId,
				List.of(AssistantMessage.builder()
					.toolCalls(List.of(new AssistantMessage.ToolCall("call1", "function", "first", "{}")))
					.build()));
		this.chatMemoryRepository.saveAll(secondConversationId,
				List.of(AssistantMessage.builder()
					.toolCalls(List.of(new AssistantMessage.ToolCall("call2", "function", "second", "{}")))
					.build()));

		assertThat(((AssistantMessage) this.chatMemoryRepository.findByConversationId(firstConversationId).get(0))
			.getToolCalls()).extracting(AssistantMessage.ToolCall::name).containsExactly("first");
		assertThat(((AssistantMessage) this.chatMemoryRepository.findByConversationId(secondConversationId).get(0))
			.getToolCalls()).extracting(AssistantMessage.ToolCall::name).containsExactly("second");
		assertThat(this.chatMemoryRepository.findConversationIds()).contains(firstConversationId, secondConversationId);
	}

	@Test
	void savingAnEmptyMessageListLeavesNoDocuments() {
		var conversationId = UUID.randomUUID().toString();

		this.chatMemoryRepository.saveAll(conversationId, List.of());

		assertThat(this.chatMemoryRepository.findByConversationId(conversationId)).isEmpty();
	}

	@Test
	void toolResponseMessagesSavedWithoutResponsesArePreserved() {
		var conversationId = UUID.randomUUID().toString();
		// An empty response list is stored as an empty array, which is distinguishable
		// from a legacy document that has no tool response field at all.
		var toolResponse = ToolResponseMessage.builder().responses(List.of()).build();

		this.chatMemoryRepository.saveAll(conversationId, List.of(toolResponse));

		var results = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(results).hasSize(1);
		assertThat(results.get(0)).isInstanceOf(ToolResponseMessage.class);
		assertThat(((ToolResponseMessage) results.get(0)).getResponses()).isEmpty();
	}

	@Test
	void largeToolCallArgumentsAndResponseDataAreRoundTripped() {
		var conversationId = UUID.randomUUID().toString();
		var largeValue = "x".repeat(64 * 1024);
		var toolCall = new AssistantMessage.ToolCall("call1", "function", "bulk",
				"{\"payload\":\"" + largeValue + "\"}");
		var toolResponse = new ToolResponseMessage.ToolResponse("call1", "bulk", largeValue);

		this.chatMemoryRepository.saveAll(conversationId,
				List.of(AssistantMessage.builder().toolCalls(List.of(toolCall)).build(),
						ToolResponseMessage.builder().responses(List.of(toolResponse)).build()));

		var results = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(((AssistantMessage) results.get(0)).getToolCalls()).containsExactly(toolCall);
		assertThat(((ToolResponseMessage) results.get(1)).getResponses()).containsExactly(toolResponse);
	}

	@Test
	void toolCallsWithEmptyArgumentsAreRoundTripped() {
		var conversationId = UUID.randomUUID().toString();
		var toolCall = new AssistantMessage.ToolCall("call1", "function", "noArgs", "");

		this.chatMemoryRepository.saveAll(conversationId,
				List.of(AssistantMessage.builder().toolCalls(List.of(toolCall)).build()));

		var results = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(((AssistantMessage) results.get(0)).getToolCalls()).containsExactly(toolCall);
	}

	@Test
	void assistantMessagesWithBothTextAndToolCallsAreRoundTripped() {
		var conversationId = UUID.randomUUID().toString();
		var toolCall = new AssistantMessage.ToolCall("call1", "function", "getWeather", "{}");
		var assistant = AssistantMessage.builder().content("Let me check.").toolCalls(List.of(toolCall)).build();

		this.chatMemoryRepository.saveAll(conversationId, List.of(assistant));

		var results = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(results).containsExactly(assistant);
		assertThat(results.get(0).getText()).isEqualTo("Let me check.");
		assertThat(((AssistantMessage) results.get(0)).getToolCalls()).containsExactly(toolCall);
	}

	@Test
	void legacyToolDocumentsWithoutToolResponsesAreSkipped() {
		var conversationId = UUID.randomUUID().toString();
		var timestamp = Date.from(Instant.now());
		// Documents written before tool call persistence was supported have neither the
		// tool response nor the sequence identifier fields.
		this.mongoTemplate.getCollection("ai_chat_memory")
			.insertMany(List.of(
					new Document(Map.of("conversationId", conversationId, "message",
							new Document(
									Map.of("content", "Hello", "type", MessageType.USER.name(), "metadata", Map.of())),
							"timestamp", timestamp)),
					new Document(Map.of("conversationId", conversationId, "message",
							new Document(Map.of("content", "", "type", MessageType.TOOL.name(), "metadata", Map.of())),
							"timestamp", timestamp))));

		var results = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(results).hasSize(1);
		assertThat(results.get(0).getText()).isEqualTo("Hello");
	}

	@Test
	void legacyAssistantDocumentsWithoutToolCallsAreReadBack() {
		var conversationId = UUID.randomUUID().toString();
		// A document written before tool call persistence was supported has no toolCalls
		// field at all, which must be read back as an empty list rather than failing.
		this.mongoTemplate.getCollection("ai_chat_memory")
			.insertOne(new Document(Map.of(
					"conversationId", conversationId, "message", new Document(Map.of("content", "Legacy answer", "type",
							MessageType.ASSISTANT.name(), "metadata", Map.of())),
					"timestamp", Date.from(Instant.now()))));

		var results = this.chatMemoryRepository.findByConversationId(conversationId);
		assertThat(results).hasSize(1);
		assertThat(results.get(0).getText()).isEqualTo("Legacy answer");
		assertThat(((AssistantMessage) results.get(0)).getToolCalls()).isEmpty();
	}

	@Test
	void legacyDocumentsAreOrderedBeforeNewlyWrittenOnes() {
		var conversationId = UUID.randomUUID().toString();
		this.mongoTemplate.getCollection("ai_chat_memory")
			.insertOne(new Document(Map.of("conversationId", conversationId, "message",
					new Document(Map.of("content", "Legacy", "type", MessageType.USER.name(), "metadata", Map.of())),
					"timestamp", Date.from(Instant.now().minusSeconds(60)))));

		var stored = this.mongoTemplate.query(Conversation.class)
			.matching(Query.query(Criteria.where("conversationId").is(conversationId)))
			.firstValue();
		assertThat(stored).isNotNull();
		// An absent sequence identifier is read back as zero.
		assertThat(stored.sequenceId()).isZero();
		assertThat(this.chatMemoryRepository.findByConversationId(conversationId)).extracting(Message::getText)
			.containsExactly("Legacy");
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
