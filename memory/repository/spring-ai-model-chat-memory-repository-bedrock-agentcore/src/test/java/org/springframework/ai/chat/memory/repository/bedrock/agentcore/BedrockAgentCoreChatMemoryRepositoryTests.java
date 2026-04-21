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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.Content;
import software.amazon.awssdk.services.bedrockagentcore.model.Conversational;
import software.amazon.awssdk.services.bedrockagentcore.model.CreateEventRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.CreateEventResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.DeleteEventRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.DeleteEventResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.DeleteMemoryRecordRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.DeleteMemoryRecordResponse;
import software.amazon.awssdk.services.bedrockagentcore.model.Event;
import software.amazon.awssdk.services.bedrockagentcore.model.ListEventsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.ListMemoryRecordsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.ListSessionsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryContent;
import software.amazon.awssdk.services.bedrockagentcore.model.MemoryRecordSummary;
import software.amazon.awssdk.services.bedrockagentcore.model.PayloadType;
import software.amazon.awssdk.services.bedrockagentcore.model.RetrieveMemoryRecordsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;
import software.amazon.awssdk.services.bedrockagentcore.model.SessionSummary;
import software.amazon.awssdk.services.bedrockagentcore.paginators.ListEventsIterable;
import software.amazon.awssdk.services.bedrockagentcore.paginators.ListMemoryRecordsIterable;
import software.amazon.awssdk.services.bedrockagentcore.paginators.ListSessionsIterable;
import software.amazon.awssdk.services.bedrockagentcore.paginators.RetrieveMemoryRecordsIterable;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BedrockAgentCoreChatMemoryRepository}.
 *
 * @author Chaemin Lee
 */
@ExtendWith(MockitoExtension.class)
class BedrockAgentCoreChatMemoryRepositoryTests {

	@Mock
	BedrockAgentCoreClient client;

	BedrockAgentCoreChatMemoryRepository repository;

	@BeforeEach
	void setUp() {
		this.repository = BedrockAgentCoreChatMemoryRepository.builder()
			.bedrockAgentCoreClient(this.client)
			.memoryId("test-store")
			.build();
	}

	@Test
	void builderRejectsNullClient() {
		assertThatThrownBy(() -> BedrockAgentCoreChatMemoryRepository.builder().memoryId("test-store").build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("bedrockAgentCoreClient");
	}

	@Test
	void builderRejectsBlankMemoryId() {
		assertThatThrownBy(() -> BedrockAgentCoreChatMemoryRepository.builder()
			.bedrockAgentCoreClient(this.client)
			.memoryId("")
			.build()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("memoryId");
	}

	@Test
	void saveAllRejectsNullMessages() {
		assertThatThrownBy(() -> this.repository.saveAll("conv-1", null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("messages");
	}

	@Test
	void saveAllRejectsNullMessageElement() {
		assertThatThrownBy(() -> this.repository.saveAll("conv-1", java.util.Arrays.asList(null, null)))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("messages");
	}

	@Test
	void saveAllAndFindByConversationIdRoundTrip() {
		UserMessage userMsg = UserMessage.builder().text("Hello").build();
		AssistantMessage assistantMsg = AssistantMessage.builder().content("Hi there").build();

		Event event1 = Event.builder()
			.eventId("evt-1")
			.sessionId("conv-1")
			.payload(PayloadType.fromConversational(
					Conversational.builder().role(Role.USER).content(Content.builder().text("Hello").build()).build()))
			.build();
		Event event2 = Event.builder()
			.eventId("evt-2")
			.sessionId("conv-1")
			.payload(PayloadType.fromConversational(Conversational.builder()
				.role(Role.ASSISTANT)
				.content(Content.builder().text("Hi there").build())
				.build()))
			.build();

		// First call: empty (inside deleteByConversationId within saveAll)
		// Second call: returns the two saved events (findByConversationId)
		ListEventsIterable emptyPage = mockEventsPaginator(List.of());
		ListEventsIterable eventsPage = mockEventsPaginator(List.of(event1, event2));
		when(this.client.listEventsPaginator(any(ListEventsRequest.class))).thenReturn(emptyPage)
			.thenReturn(eventsPage);
		when(this.client.createEvent(any(CreateEventRequest.class))).thenReturn(CreateEventResponse.builder().build());

		this.repository.saveAll("conv-1", List.of(userMsg, assistantMsg));
		List<Message> result = this.repository.findByConversationId("conv-1");

		assertThat(result).hasSize(2);
		ArgumentCaptor<ListEventsRequest> listCaptor = ArgumentCaptor.forClass(ListEventsRequest.class);
		verify(this.client, times(2)).listEventsPaginator(listCaptor.capture());
		assertThat(listCaptor.getAllValues())
			.allMatch(r -> BedrockAgentCoreChatMemoryConfig.DEFAULT_ACTOR_ID.equals(r.actorId()));
		assertThat(result.get(0).getText()).isEqualTo("Hello");
		assertThat(result.get(1).getText()).isEqualTo("Hi there");
		verify(this.client, times(2)).createEvent(any(CreateEventRequest.class));
	}

	@Test
	void findByConversationIdWithActorIdUsesActorInListEventsRequest() {
		ListEventsIterable emptyPage = mockEventsPaginator(List.of());
		when(this.client.listEventsPaginator(any(ListEventsRequest.class))).thenReturn(emptyPage);

		this.repository.findByConversationId("actor-z", "conv-9");

		ArgumentCaptor<ListEventsRequest> captor = ArgumentCaptor.forClass(ListEventsRequest.class);
		verify(this.client).listEventsPaginator(captor.capture());
		assertThat(captor.getValue().actorId()).isEqualTo("actor-z");
		assertThat(captor.getValue().sessionId()).isEqualTo("conv-9");
	}

	@Test
	void saveAllWithActorIdUsesActorInCreateEvent() {
		UserMessage userMsg = UserMessage.builder().text("Hi").build();
		ListEventsIterable emptyPage = mockEventsPaginator(List.of());
		when(this.client.listEventsPaginator(any(ListEventsRequest.class))).thenReturn(emptyPage);
		when(this.client.createEvent(any(CreateEventRequest.class))).thenReturn(CreateEventResponse.builder().build());

		this.repository.saveAll("actor-x", "conv-7", List.of(userMsg));

		ArgumentCaptor<CreateEventRequest> captor = ArgumentCaptor.forClass(CreateEventRequest.class);
		verify(this.client).createEvent(captor.capture());
		assertThat(captor.getValue().actorId()).isEqualTo("actor-x");
		assertThat(captor.getValue().sessionId()).isEqualTo("conv-7");
	}

	@Test
	void saveAllWithEmptyListDeletesExistingMessages() {
		Event event1 = Event.builder().eventId("evt-1").sessionId("conv-1").build();
		ListEventsIterable page = mockEventsPaginator(List.of(event1));
		when(this.client.listEventsPaginator(any(ListEventsRequest.class))).thenReturn(page);
		when(this.client.deleteEvent(any(DeleteEventRequest.class))).thenReturn(DeleteEventResponse.builder().build());

		this.repository.saveAll("conv-1", List.of());

		verify(this.client).listEventsPaginator(any(ListEventsRequest.class));
		verify(this.client).deleteEvent(any(DeleteEventRequest.class));
		verify(this.client, never()).createEvent(any(CreateEventRequest.class));
	}

	@Test
	void deleteByConversationIdDeletesEachEvent() {
		Event event1 = Event.builder().eventId("evt-1").sessionId("conv-1").build();
		Event event2 = Event.builder().eventId("evt-2").sessionId("conv-1").build();

		ListEventsIterable page = mockEventsPaginator(List.of(event1, event2));
		when(this.client.listEventsPaginator(any(ListEventsRequest.class))).thenReturn(page);
		when(this.client.deleteEvent(any(DeleteEventRequest.class))).thenReturn(DeleteEventResponse.builder().build());

		this.repository.deleteByConversationId("conv-1");

		verify(this.client, times(2)).deleteEvent(any(DeleteEventRequest.class));
	}

	@Test
	void deleteByConversationIdWithActorIdUsesActorInDeleteEvent() {
		Event event1 = Event.builder().eventId("evt-1").sessionId("conv-1").build();
		ListEventsIterable page = mockEventsPaginator(List.of(event1));
		when(this.client.listEventsPaginator(any(ListEventsRequest.class))).thenReturn(page);
		when(this.client.deleteEvent(any(DeleteEventRequest.class))).thenReturn(DeleteEventResponse.builder().build());

		this.repository.deleteByConversationId("actor-y", "conv-1");

		ArgumentCaptor<DeleteEventRequest> captor = ArgumentCaptor.forClass(DeleteEventRequest.class);
		verify(this.client).deleteEvent(captor.capture());
		assertThat(captor.getValue().actorId()).isEqualTo("actor-y");
	}

	@Test
	void deleteByConversationIdIsNoOpWhenNoEvents() {
		ListEventsIterable emptyPage = mockEventsPaginator(List.of());
		when(this.client.listEventsPaginator(any(ListEventsRequest.class))).thenReturn(emptyPage);

		this.repository.deleteByConversationId("conv-does-not-exist");

		verify(this.client, never()).deleteEvent(any(DeleteEventRequest.class));
	}

	@Test
	void retrieveMemoryRecordsReturnsResults() {
		MemoryRecordSummary summary = MemoryRecordSummary.builder()
			.memoryRecordId("mem-001")
			.content(MemoryContent.fromText("the user prefers dark mode"))
			.score(Double.valueOf(0.95))
			.build();
		RetrieveMemoryRecordsIterable paginator = mockRetrieveMemoryRecordsPaginator(List.of(summary));
		when(this.client.retrieveMemoryRecordsPaginator(any(RetrieveMemoryRecordsRequest.class))).thenReturn(paginator);

		List<MemoryRecordSummary> result = this.repository.retrieveMemoryRecords("user123", "UI preferences");

		assertThat(result).hasSize(1);
		assertThat(result.get(0).memoryRecordId()).isEqualTo("mem-001");
		assertThat(result.get(0).score()).isEqualTo(0.95);
		verify(this.client).retrieveMemoryRecordsPaginator(any(RetrieveMemoryRecordsRequest.class));
	}

	@Test
	void retrieveMemoryRecordsWithPaginationCollectsAllPages() {
		MemoryRecordSummary summary1 = MemoryRecordSummary.builder()
			.memoryRecordId("mem-001")
			.score(Double.valueOf(0.9))
			.build();
		MemoryRecordSummary summary2 = MemoryRecordSummary.builder()
			.memoryRecordId("mem-002")
			.score(Double.valueOf(0.8))
			.build();
		RetrieveMemoryRecordsIterable paginator = mockRetrieveMemoryRecordsPaginator(List.of(summary1, summary2));
		when(this.client.retrieveMemoryRecordsPaginator(any(RetrieveMemoryRecordsRequest.class))).thenReturn(paginator);

		List<MemoryRecordSummary> result = this.repository.retrieveMemoryRecords("user123", "preferences");

		assertThat(result).hasSize(2);
		assertThat(result).extracting(MemoryRecordSummary::memoryRecordId).containsExactly("mem-001", "mem-002");
		verify(this.client).retrieveMemoryRecordsPaginator(any(RetrieveMemoryRecordsRequest.class));
	}

	@Test
	void retrieveMemoryRecordsRejectsBlankNamespace() {
		assertThatThrownBy(() -> this.repository.retrieveMemoryRecords("", "query"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("namespace");
	}

	@Test
	void retrieveMemoryRecordsRejectsBlankSearchQuery() {
		assertThatThrownBy(() -> this.repository.retrieveMemoryRecords("user123", ""))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("searchQuery");
	}

	@Test
	void listMemoryRecordsReturnsResults() {
		MemoryRecordSummary summary = MemoryRecordSummary.builder()
			.memoryRecordId("mem-010")
			.content(MemoryContent.fromText("user lives in Seoul"))
			.build();
		ListMemoryRecordsIterable paginator = mockListMemoryRecordsPaginator(List.of(summary));
		when(this.client.listMemoryRecordsPaginator(any(ListMemoryRecordsRequest.class))).thenReturn(paginator);

		List<MemoryRecordSummary> result = this.repository.listMemoryRecords("user123");

		assertThat(result).hasSize(1);
		assertThat(result.get(0).memoryRecordId()).isEqualTo("mem-010");
		verify(this.client).listMemoryRecordsPaginator(any(ListMemoryRecordsRequest.class));
	}

	@Test
	void listMemoryRecordsWithPaginationCollectsAllPages() {
		MemoryRecordSummary s1 = MemoryRecordSummary.builder().memoryRecordId("mem-010").build();
		MemoryRecordSummary s2 = MemoryRecordSummary.builder().memoryRecordId("mem-011").build();
		ListMemoryRecordsIterable paginator = mockListMemoryRecordsPaginator(List.of(s1, s2));
		when(this.client.listMemoryRecordsPaginator(any(ListMemoryRecordsRequest.class))).thenReturn(paginator);

		List<MemoryRecordSummary> result = this.repository.listMemoryRecords("user123");

		assertThat(result).hasSize(2);
		assertThat(result).extracting(MemoryRecordSummary::memoryRecordId).containsExactly("mem-010", "mem-011");
		verify(this.client).listMemoryRecordsPaginator(any(ListMemoryRecordsRequest.class));
	}

	@Test
	void listMemoryRecordsRejectsBlankNamespace() {
		assertThatThrownBy(() -> this.repository.listMemoryRecords("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("namespace");
	}

	@Test
	void deleteMemoryRecordCallsClient() {
		when(this.client.deleteMemoryRecord(any(DeleteMemoryRecordRequest.class)))
			.thenReturn(DeleteMemoryRecordResponse.builder().memoryRecordId("mem-999").build());

		this.repository.deleteMemoryRecord("mem-999");

		verify(this.client).deleteMemoryRecord(any(DeleteMemoryRecordRequest.class));
	}

	@Test
	void deleteMemoryRecordWrapsExceptionAsRuntimeException() {
		when(this.client.deleteMemoryRecord(any(DeleteMemoryRecordRequest.class)))
			.thenThrow(new RuntimeException("AWS error"));

		assertThatThrownBy(() -> this.repository.deleteMemoryRecord("mem-999")).isInstanceOf(RuntimeException.class)
			.hasMessageContaining("mem-999");
	}

	@Test
	void deleteMemoryRecordRejectsBlankId() {
		assertThatThrownBy(() -> this.repository.deleteMemoryRecord("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("memoryRecordId");
	}

	@Test
	void findConversationIdsReturnsSessionIds() {
		SessionSummary session1 = SessionSummary.builder().sessionId("conv-1").build();
		SessionSummary session2 = SessionSummary.builder().sessionId("conv-2").build();
		ListSessionsIterable paginator = mockSessionsPaginator(List.of(session1, session2));
		when(this.client.listSessionsPaginator(any(ListSessionsRequest.class))).thenReturn(paginator);

		List<String> ids = this.repository.findConversationIds();

		assertThat(ids).containsExactly("conv-1", "conv-2");
		ArgumentCaptor<ListSessionsRequest> captor = ArgumentCaptor.forClass(ListSessionsRequest.class);
		verify(this.client).listSessionsPaginator(captor.capture());
		assertThat(captor.getValue().actorId()).isEqualTo(BedrockAgentCoreChatMemoryConfig.DEFAULT_ACTOR_ID);
	}

	@Test
	void findConversationIdsWithActorIdUsesActorInRequest() {
		ListSessionsIterable paginator = mockSessionsPaginator(List.of());
		when(this.client.listSessionsPaginator(any(ListSessionsRequest.class))).thenReturn(paginator);

		this.repository.findConversationIds("user-42");

		ArgumentCaptor<ListSessionsRequest> captor = ArgumentCaptor.forClass(ListSessionsRequest.class);
		verify(this.client).listSessionsPaginator(captor.capture());
		assertThat(captor.getValue().actorId()).isEqualTo("user-42");
	}

	@Test
	void findConversationIdsWithActorIdRejectsBlankActorId() {
		assertThatThrownBy(() -> this.repository.findConversationIds("")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("actorId");
	}

	@Test
	void findConversationIdsReturnsEmptyWhenNoSessions() {
		ListSessionsIterable paginator = mockSessionsPaginator(List.of());
		when(this.client.listSessionsPaginator(any(ListSessionsRequest.class))).thenReturn(paginator);

		List<String> ids = this.repository.findConversationIds();

		assertThat(ids).isEmpty();
	}

	private ListSessionsIterable mockSessionsPaginator(List<SessionSummary> sessions) {
		ListSessionsIterable paginator = mock(ListSessionsIterable.class);
		SdkIterable<SessionSummary> sdkSessions = sessions::iterator;
		when(paginator.sessionSummaries()).thenReturn(sdkSessions);
		return paginator;
	}

	private ListEventsIterable mockEventsPaginator(List<Event> events) {
		ListEventsIterable paginator = mock(ListEventsIterable.class);
		SdkIterable<Event> sdkEvents = events::iterator;
		when(paginator.events()).thenReturn(sdkEvents);
		return paginator;
	}

	private RetrieveMemoryRecordsIterable mockRetrieveMemoryRecordsPaginator(List<MemoryRecordSummary> summaries) {
		RetrieveMemoryRecordsIterable paginator = mock(RetrieveMemoryRecordsIterable.class);
		SdkIterable<MemoryRecordSummary> sdkSummaries = summaries::iterator;
		when(paginator.memoryRecordSummaries()).thenReturn(sdkSummaries);
		return paginator;
	}

	private ListMemoryRecordsIterable mockListMemoryRecordsPaginator(List<MemoryRecordSummary> summaries) {
		ListMemoryRecordsIterable paginator = mock(ListMemoryRecordsIterable.class);
		SdkIterable<MemoryRecordSummary> sdkSummaries = summaries::iterator;
		when(paginator.memoryRecordSummaries()).thenReturn(sdkSummaries);
		return paginator;
	}

}
