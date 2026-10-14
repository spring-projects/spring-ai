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

package org.springframework.ai.session;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.compaction.CompactionResult;
import org.springframework.ai.session.compaction.WindowCompactionStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for session lifecycle, event management and compaction strategies —
 * exercised end-to-end through a Spring Boot application context with
 * {@link DefaultSessionService} and {@link InMemorySessionRepository}.
 *
 * @author Christian Tzolov
 */
@SpringBootTest(classes = SessionMemoryTests.TestConfig.class)
class SessionMemoryTests {

	@Autowired
	SessionService sessionService;

	// --- Session lifecycle ---

	@Test
	void createSessionAndFindById() {
		Session session = this.sessionService
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "alice").build());

		assertThat(session.id()).isNotBlank();
		assertThat(SessionAttributes.principalName(session)).isEqualTo("alice");
		assertThat(session.createdAt()).isNotNull();

		Session found = this.sessionService.findById(session.id());
		assertThat(found).isNotNull();
		assertThat(found.id()).isEqualTo(session.id());
	}

	@Test
	void findByIndexReturnsAllSessionsOfPrincipal() {
		this.sessionService
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "bob").build());
		this.sessionService
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "bob").build());
		this.sessionService
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "carol").build());

		List<Session> bobSessions = this.sessionService.findByIndex(SessionAttributes.PRINCIPAL_NAME, "bob");
		assertThat(bobSessions).hasSizeGreaterThanOrEqualTo(2);
		assertThat(bobSessions).allMatch(s -> "bob".equals(SessionAttributes.principalName(s)));
	}

	@Test
	void deleteRemovesSessionCompletely() {
		Session session = this.sessionService
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "eve").build());

		this.sessionService.delete(session.id());

		assertThat(this.sessionService.findById(session.id())).isNull();
	}

	@Test
	void sessionWithTimeToLiveHasExpiresAt() {
		Session session = this.sessionService.create(CreateSessionRequest.builder()
			.metadata(SessionAttributes.PRINCIPAL_NAME, "frank")
			.timeToLive(Duration.ofHours(1))
			.build());

		assertThat(session.expiresAt()).isNotNull();
		assertThat(session.expiresAt()).isAfter(session.createdAt());
	}

	// --- Event management ---

	@Test
	void appendMessagesAndRetrieveInOrder() {
		Session session = this.sessionService
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-1").build());

		this.sessionService.appendMessage(session.id(), new UserMessage("What is Spring AI?"));
		this.sessionService.appendMessage(session.id(), new AssistantMessage("Spring AI is a framework."));
		this.sessionService.appendMessage(session.id(), new UserMessage("Tell me more."));

		List<Message> messages = this.sessionService.getMessages(session.id());

		assertThat(messages).hasSize(3);
		assertThat(messages.get(0).getText()).isEqualTo("What is Spring AI?");
		assertThat(messages.get(1).getText()).isEqualTo("Spring AI is a framework.");
		assertThat(messages.get(2).getText()).isEqualTo("Tell me more.");
	}

	@Test
	void getEventsWithLastNFilterReturnsCorrectSlice() {
		Session session = this.sessionService
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-2").build());

		for (int i = 1; i <= 6; i++) {
			this.sessionService.appendMessage(session.id(), new UserMessage("message " + i));
		}

		List<SessionEvent> last3 = this.sessionService.getEvents(session.id(), EventFilter.lastN(3));

		assertThat(last3).hasSize(3);
		assertThat(last3.get(0).getMessage().getText()).isEqualTo("message 4");
		assertThat(last3.get(2).getMessage().getText()).isEqualTo("message 6");
	}

	@Test
	void excludeSyntheticFilterHidesSummaryEvents() {
		Session session = this.sessionService
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-4").build());

		this.sessionService.appendMessage(session.id(), new UserMessage("Hello"));
		this.sessionService.appendMessage(session.id(), new AssistantMessage("Hi there!"));

		// Inject synthetic summary events directly
		this.sessionService.appendEvent(SessionEvent.builder()
			.sessionId(session.id())
			.message(new UserMessage("Summarize the conversation so far."))
			.metadata(SessionEvent.METADATA_SYNTHETIC, true)
			.metadata(SessionEvent.METADATA_COMPACTION_SOURCE, "window")
			.build());
		this.sessionService.appendEvent(SessionEvent.builder()
			.sessionId(session.id())
			.message(new AssistantMessage("User said Hello, assistant replied Hi."))
			.metadata(SessionEvent.METADATA_SYNTHETIC, true)
			.metadata(SessionEvent.METADATA_COMPACTION_SOURCE, "window")
			.build());

		List<SessionEvent> allEvents = this.sessionService.getEvents(session.id());
		assertThat(allEvents).hasSize(4);

		List<SessionEvent> realOnly = this.sessionService.getEvents(session.id(),
				EventFilter.builder().excludeSynthetic(true).build());
		assertThat(realOnly).hasSize(2);
		assertThat(realOnly).noneMatch(SessionEvent::isSynthetic);
	}

	// --- Compaction strategies ---

	@Test
	void slidingWindowCompactionKeepsLastNEvents() {
		Session session = this.sessionService
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-5").build());

		for (int i = 1; i <= 6; i++) {
			this.sessionService.appendMessage(session.id(), new UserMessage("user turn " + i));
			this.sessionService.appendMessage(session.id(), new AssistantMessage("assistant reply " + i));
		}

		CompactionResult result = this.sessionService.compact(session.id(), req -> true,
				WindowCompactionStrategy.builder().lastEvents(4).build());

		assertThat(result.coveredEventCount()).isPositive();
		assertThat(result.preservedEvents()).hasSizeLessThanOrEqualTo(4);

		// The active window is trimmed to the kept events...
		List<SessionEvent> active = this.sessionService.getEvents(session.id(), EventFilter.active());
		assertThat(active).hasSizeLessThanOrEqualTo(4);
		// Window must start on a turn boundary (user message)
		assertThat(active.get(0).getMessageType().getValue()).isEqualTo("user");

		// ...but covered events are retained for Recall Storage (nothing is deleted)
		List<Message> all = this.sessionService.getMessages(session.id());
		assertThat(all).hasSize(12);
	}

	@Test
	void storedSystemMessageSurvivesCompactionAndStaysFirst() {
		Session session = this.sessionService
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-sys").build());
		this.sessionService.appendMessage(session.id(), new SystemMessage("Answer in French."));
		for (int i = 1; i <= 5; i++) {
			this.sessionService.appendMessage(session.id(), new UserMessage("question " + i));
			this.sessionService.appendMessage(session.id(), new AssistantMessage("answer " + i));
		}

		CompactionResult result = this.sessionService.compact(session.id(), req -> true,
				WindowCompactionStrategy.builder().lastEvents(2).build());

		assertThat(result.coveredEvents()).extracting(e -> e.getMessage().getText())
			.doesNotContain("Answer in French.");
		assertThat(this.sessionService.getActiveMessages(session.id())).extracting(Message::getText)
			.containsExactly("Answer in French.", "question 5", "answer 5");
	}

	@Test
	void turnWindowCompactionKeepsLastNTurns() {
		Session session = this.sessionService
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-6").build());

		for (int i = 1; i <= 5; i++) {
			this.sessionService.appendMessage(session.id(), new UserMessage("user turn " + i));
			this.sessionService.appendMessage(session.id(), new AssistantMessage("assistant reply " + i));
		}

		CompactionResult result = this.sessionService.compact(session.id(), req -> true,
				WindowCompactionStrategy.builder().lastTurns(2).build());

		assertThat(result.coveredEventCount()).isPositive();

		// Active window: 2 turns × 2 messages per turn = 4 messages
		List<SessionEvent> active = this.sessionService.getEvents(session.id(), EventFilter.active());
		assertThat(active).hasSize(4);
		assertThat(active.get(0).getMessage().getText()).isEqualTo("user turn 4");
		assertThat(active.get(3).getMessage().getText()).isEqualTo("assistant reply 5");

		// Covered turns are retained (not deleted) — full history still available
		assertThat(this.sessionService.getMessages(session.id())).hasSize(10);
		// ...while getActiveMessages() returns only the active window
		assertThat(this.sessionService.getActiveMessages(session.id())).extracting(m -> m.getText())
			.containsExactly("user turn 4", "assistant reply 4", "user turn 5", "assistant reply 5");
	}

	@Test
	void tokenCountCompactionDropsOldestEventsOverBudget() {
		Session session = this.sessionService
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-7").build());

		// Add messages — each "token" word approximation ~1 token each
		for (int i = 1; i <= 10; i++) {
			this.sessionService.appendMessage(session.id(), new UserMessage("short msg " + i));
		}

		// Very small token budget forces compaction
		CompactionResult result = this.sessionService.compact(session.id(), req -> true,
				WindowCompactionStrategy.builder().tokenBudget(20).build());

		assertThat(result.coveredEventCount()).isPositive();
		assertThat(result.preservedEvents().size()).isLessThan(10);
		// Kept window must start on a user message (turn boundary)
		assertThat(result.preservedEvents().get(0).getMessageType().getValue()).isEqualTo("user");
	}

	@Test
	void compactionIsNoopWhenEventsWithinBudget() {
		Session session = this.sessionService
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-8").build());

		this.sessionService.appendMessage(session.id(), new UserMessage("hi"));
		this.sessionService.appendMessage(session.id(), new AssistantMessage("hello"));

		// Large window — no compaction needed
		CompactionResult result = this.sessionService.compact(session.id(), req -> true,
				WindowCompactionStrategy.builder().lastEvents(100).build());

		assertThat(result.isUnchanged()).isTrue();
	}

	@Test
	void syntheticSummaryEventsArePreservedAfterCompaction() {
		Session session = this.sessionService
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-9").build());

		// Add some real events
		for (int i = 1; i <= 4; i++) {
			this.sessionService.appendMessage(session.id(), new UserMessage("msg " + i));
		}

		// Inject a synthetic summary
		this.sessionService.appendEvent(SessionEvent.builder()
			.sessionId(session.id())
			.message(new UserMessage("Summarize the conversation."))
			.metadata(SessionEvent.METADATA_SYNTHETIC, true)
			.metadata(SessionEvent.METADATA_COMPACTION_SOURCE, "window")
			.build());
		this.sessionService.appendEvent(SessionEvent.builder()
			.sessionId(session.id())
			.message(new AssistantMessage("User sent 4 messages."))
			.metadata(SessionEvent.METADATA_SYNTHETIC, true)
			.metadata(SessionEvent.METADATA_COMPACTION_SOURCE, "window")
			.build());

		// Compact aggressively — only 1 real event slot
		CompactionResult result = this.sessionService.compact(session.id(), req -> true,
				WindowCompactionStrategy.builder().lastEvents(1).build());

		// Synthetic events must survive compaction: a window pins the prior summary
		assertThat(result.pinnedEvents()).hasSize(2).allMatch(SessionEvent::isSynthetic);
		assertThat(this.sessionService.getActiveMessages(session.id())).extracting(Message::getText)
			.startsWith("Summarize the conversation.", "User sent 4 messages.");
	}

	// --- Spring Boot configuration ---

	@SpringBootConfiguration
	static class TestConfig {

		@Bean
		SessionRepository sessionRepository() {
			return InMemorySessionRepository.builder().build();
		}

		@Bean
		SessionService sessionService(SessionRepository sessionRepository) {
			// Some tests store system messages on purpose (session setup).
			return DefaultSessionService.builder()
				.sessionRepository(sessionRepository)
				.allowSystemMessages(true)
				.build();
		}

	}

}
