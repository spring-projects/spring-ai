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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.compaction.CompactionBoundary;
import org.springframework.ai.session.compaction.CompactionResult;
import org.springframework.ai.session.compaction.CompactionStrategy;
import org.springframework.ai.session.compaction.WindowCompactionStrategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link DefaultSessionService}.
 */
class DefaultSessionServiceTests {

	private SessionService service;

	@BeforeEach
	void setUp() {
		this.service = DefaultSessionService.builder()
			.sessionRepository(InMemorySessionRepository.builder().build())
			.build();
	}

	@Test
	void storingSystemMessageIsRejectedByDefault() {
		Session session = this.service
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-1").build());

		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.service.appendMessage(session.id(), new SystemMessage("Answer in French.")))
			.withMessageContaining(session.id())
			.withMessageContaining("allowSystemMessages(true)")
			.withMessageContaining("spring.ai.session.allow-system-messages=true");
		assertThat(this.service.getEvents(session.id())).isEmpty();
	}

	@Test
	void storingSystemMessageIsAllowedWhenEnabled() {
		SessionService permissive = DefaultSessionService.builder()
			.sessionRepository(InMemorySessionRepository.builder().build())
			.allowSystemMessages(true)
			.build();
		Session session = permissive
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-1").build());

		permissive.appendMessage(session.id(), new SystemMessage("Answer in French."));

		assertThat(permissive.getMessages(session.id())).extracting(Message::getText)
			.containsExactly("Answer in French.");
	}

	@Test
	void conversationMessagesAreUnaffectedBySystemMessageRule() {
		Session session = this.service
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-1").build());

		this.service.appendMessage(session.id(), new UserMessage("hi"));
		this.service.appendMessage(session.id(), new AssistantMessage("hello"));
		this.service.appendMessage(session.id(),
				ToolResponseMessage.builder()
					.responses(List.of(new ToolResponseMessage.ToolResponse("call-1", "tool", "ok")))
					.build());

		assertThat(this.service.getEvents(session.id())).hasSize(3);
	}

	@Test
	void createReturnsSession() {
		Session session = this.service
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-1").build());

		assertThat(session).isNotNull();
		assertThat(session.id()).isNotBlank();
		assertThat(SessionAttributes.principalName(session)).isEqualTo("user-1");
		assertThat(this.service.getEvents(session.id())).isEmpty();
	}

	@Test
	void createAppliesDefaultTimeToLiveWhenRequestHasNone() {
		// The service's default is 60 days
		Instant before = Instant.now();
		Session session = this.service
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-1").build());

		assertThat(session.expiresAt()).isBetween(before.plus(Duration.ofDays(60)).minusSeconds(5),
				Instant.now().plus(Duration.ofDays(60)).plusSeconds(5));
	}

	@Test
	void createHonoursConfiguredDefaultTimeToLive() {
		SessionService customService = DefaultSessionService.builder()
			.sessionRepository(InMemorySessionRepository.builder().build())
			.defaultTimeToLive(Duration.ofHours(2))
			.build();

		Instant before = Instant.now();
		Session session = customService
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-1").build());

		assertThat(session.expiresAt()).isBetween(before.plus(Duration.ofHours(2)).minusSeconds(5),
				Instant.now().plus(Duration.ofHours(2)).plusSeconds(5));
	}

	@Test
	void createPrefersRequestTimeToLiveOverDefault() {
		Instant before = Instant.now();
		Session session = this.service.create(CreateSessionRequest.builder()
			.metadata(SessionAttributes.PRINCIPAL_NAME, "user-1")
			.timeToLive(Duration.ofMinutes(30))
			.build());

		assertThat(session.expiresAt()).isBefore(before.plus(Duration.ofHours(1)));
	}

	@Test
	void createWithExistingIdIsRejectedAndDoesNotReassignPrincipal() {
		Session original = this.service.create(CreateSessionRequest.builder()
			.id("fixed-id")
			.metadata(SessionAttributes.PRINCIPAL_NAME, "alice")
			.build());

		assertThatExceptionOfType(SessionAlreadyExistsException.class)
			.isThrownBy(() -> this.service.create(CreateSessionRequest.builder()
				.id("fixed-id")
				.metadata(SessionAttributes.PRINCIPAL_NAME, "mallory")
				.build()))
			.withMessageContaining("already exists")
			.satisfies(ex -> assertThat(ex.getSessionId()).isEqualTo("fixed-id"));

		assertThat(SessionAttributes.principalName(this.service.findById(original.id()))).isEqualTo("alice");
	}

	@Test
	void concurrentCreatesOfSameIdHaveExactlyOneWinner() throws Exception {
		int callers = 8;
		ExecutorService executor = Executors.newFixedThreadPool(callers);
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<Session>> results = new ArrayList<>();
			for (int i = 0; i < callers; i++) {
				String userId = "user-" + i;
				results.add(executor.submit(() -> {
					start.await();
					return this.service.create(CreateSessionRequest.builder()
						.id("contended")
						.metadata(SessionAttributes.PRINCIPAL_NAME, userId)
						.build());
				}));
			}
			start.countDown();
			List<Session> winners = new ArrayList<>();
			int rejected = 0;
			for (Future<Session> result : results) {
				try {
					winners.add(result.get(30, TimeUnit.SECONDS));
				}
				catch (ExecutionException ex) {
					assertThat(ex.getCause()).isInstanceOf(IllegalStateException.class);
					rejected++;
				}
			}
			assertThat(winners).hasSize(1);
			assertThat(rejected).isEqualTo(callers - 1);
			assertThat(SessionAttributes.principalName(this.service.findById("contended")))
				.isEqualTo(SessionAttributes.principalName(winners.get(0)));
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void appendMessageThenGetMessagesReturnsThatMessage() {
		Session session = this.service
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-1").build());

		this.service.appendMessage(session.id(), new UserMessage("hello"));

		List<Message> messages = this.service.getMessages(session.id());
		assertThat(messages).hasSize(1);
		assertThat(messages.get(0).getText()).isEqualTo("hello");
	}

	@Test
	void appendMultipleMessagesPreservesOrder() {
		Session session = this.service
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-1").build());

		this.service.appendMessage(session.id(), new UserMessage("first"));
		this.service.appendMessage(session.id(), new UserMessage("second"));
		this.service.appendMessage(session.id(), new UserMessage("third"));

		List<Message> messages = this.service.getMessages(session.id());
		assertThat(messages).hasSize(3);
		assertThat(messages.get(0).getText()).isEqualTo("first");
		assertThat(messages.get(1).getText()).isEqualTo("second");
		assertThat(messages.get(2).getText()).isEqualTo("third");
	}

	@Test
	void deleteRemovesSession() {
		Session session = this.service
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-1").build());

		this.service.delete(session.id());

		assertThat(this.service.findById(session.id())).isNull();
	}

	@Test
	void sessionHasOnlyRequestsMetadata() {
		Session session = this.service.create(CreateSessionRequest.builder().build());

		assertThat(session.metadata()).isEmpty();
		assertThat(SessionAttributes.principalName(session)).isNull();
	}

	@Test
	void findByIndexReturnsSessionsOfPrincipal() {
		this.service.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "alice").build());
		this.service.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "alice").build());
		this.service.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "bob").build());

		assertThat(this.service.findByIndex(SessionAttributes.PRINCIPAL_NAME, "alice")).hasSize(2);
		assertThat(this.service.findByIndex(SessionAttributes.PRINCIPAL_NAME, "bob")).hasSize(1);
	}

	@Test
	void compactWithEventWindowKeepsOnlyLastEvents() {
		Session session = this.service
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-1").build());

		for (int i = 1; i <= 5; i++) {
			this.service.appendMessage(session.id(), new UserMessage("msg-" + i));
		}

		CompactionResult result = this.service.compact(session.id(), req -> true,
				WindowCompactionStrategy.builder().lastEvents(2).build());

		assertThat(result.coveredEventCount()).isEqualTo(3);
		assertThat(result.preservedEvents()).hasSize(2);

		// Active window keeps only the last two events
		List<SessionEvent> active = this.service.getEvents(session.id(), EventFilter.active());
		assertThat(active).extracting(e -> e.getMessage().getText()).containsExactly("msg-4", "msg-5");

		// The three covered events stay in the log, followed by the boundary that records
		// the compaction; nothing is deleted or changed.
		List<SessionEvent> all = this.service.getEvents(session.id());
		assertThat(this.service.getMessages(session.id())).extracting(Message::getText)
			.containsExactly("msg-1", "msg-2", "msg-3", "msg-4", "msg-5");
		assertThat(all).hasSize(6);
		assertThat(all.get(5).getType()).isEqualTo(EventType.COMPACTION);
		CompactionBoundary boundary = CompactionBoundary.from(all.get(5));
		assertThat(boundary.coveredEventCount()).isEqualTo(3);
		assertThat(boundary.preservedEventIds()).containsExactly(all.get(3).getId(), all.get(4).getId());
		assertThat(boundary.summaryEventIds()).isEmpty();
		assertThat(boundary.strategy()).isEqualTo("window");
	}

	@Test
	void compactAppendsSummaryEventsAfterBoundaryAndTagsThem() {
		Session session = this.service
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-1").build());
		for (int i = 1; i <= 4; i++) {
			this.service.appendMessage(session.id(), new UserMessage("msg-" + i));
		}
		// A strategy that summarizes everything but the last event
		CompactionStrategy summarizing = request -> {
			List<SessionEvent> view = request.events();
			return new CompactionResult(view.subList(0, 3), List.of(), view.subList(3, 4),
					List.of(new UserMessage("Summarize."), new AssistantMessage("first three messages")), 10, "custom");
		};

		CompactionResult result = this.service.compact(session.id(), req -> true, summarizing);

		assertThat(result.coveredEventCount()).isEqualTo(3);
		List<SessionEvent> all = this.service.getEvents(session.id());
		assertThat(all).hasSize(7);
		SessionEvent boundary = all.get(4);
		assertThat(boundary.getType()).isEqualTo(EventType.COMPACTION);
		List<SessionEvent> summary = all.subList(5, 7);
		assertThat(summary).allMatch(SessionEvent::isSynthetic)
			.allMatch(e -> boundary.getId().equals(e.getMetadata().get(SessionEvent.METADATA_COMPACTION_ID)))
			.allMatch(e -> "custom".equals(e.getMetadata().get(SessionEvent.METADATA_COMPACTION_SOURCE)));
		assertThat(CompactionBoundary.from(boundary).summaryEventIds()).containsExactly(summary.get(0).getId(),
				summary.get(1).getId());
		assertThat(this.service.getActiveMessages(session.id())).extracting(Message::getText)
			.containsExactly("Summarize.", "first three messages", "msg-4");
	}

	@Test
	void compactedEventsRemainInLog() {
		// Regression for issue #21: compaction must cover (not delete) events, so a read
		// of
		// the whole log still finds them after the active view has moved on.
		Session session = this.service
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-1").build());
		this.service.appendMessage(session.id(), new UserMessage("the secret code is alpha-7"));
		for (int i = 1; i <= 5; i++) {
			this.service.appendMessage(session.id(), new UserMessage("filler-" + i));
		}

		this.service.compact(session.id(), req -> true, WindowCompactionStrategy.builder().lastEvents(2).build());

		// The first message is covered, out of the active window...
		assertThat(this.service.getEvents(session.id(), EventFilter.active()))
			.noneMatch(e -> e.getMessage().getText().contains("alpha-7"));
		// ...but the whole log still holds it.
		assertThat(this.service.getEvents(session.id(), EventFilter.all())).filteredOn(SessionEvent::hasMessage)
			.extracting(e -> e.getMessage().getText())
			.contains("the secret code is alpha-7");
	}

	@Test
	void compactNoOpWhenTriggerDoesNotFire() {
		Session session = this.service
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-1").build());
		this.service.appendMessage(session.id(), new UserMessage("only message"));

		// Trigger never fires — no compaction, no repository write
		CompactionResult result = this.service.compact(session.id(), req -> false,
				WindowCompactionStrategy.builder().lastEvents(10).build());

		assertThat(result.isUnchanged()).isTrue();
		assertThat(this.service.getEvents(session.id())).hasSize(1);
	}

	@Test
	void compactNoOpWhenStrategyCoversNothing() {
		Session session = this.service
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-1").build());
		this.service.appendMessage(session.id(), new UserMessage("only message"));

		// Trigger fires but window is larger than event count — strategy returns no-op
		CompactionResult result = this.service.compact(session.id(), req -> true,
				WindowCompactionStrategy.builder().lastEvents(10).build());

		assertThat(result.isUnchanged()).isTrue();
		assertThat(this.service.getEvents(session.id())).hasSize(1);
	}

	@Test
	void getEventsWithFilterAppliesFilter() {
		Session session = this.service
			.create(CreateSessionRequest.builder().metadata(SessionAttributes.PRINCIPAL_NAME, "user-1").build());

		this.service.appendMessage(session.id(), new UserMessage("a"));
		this.service.appendMessage(session.id(), new UserMessage("b"));
		this.service.appendMessage(session.id(), new UserMessage("c"));

		List<SessionEvent> last2 = this.service.getEvents(session.id(), EventFilter.lastN(2));
		assertThat(last2).hasSize(2);
		assertThat(last2.get(0).getMessage().getText()).isEqualTo("b");
		assertThat(last2.get(1).getMessage().getText()).isEqualTo("c");
	}

	@Test
	void compactionOvertakenByAppendIsNotApplied() {
		// An append between the version read and the write: the version check rejects
		// the boundary, the log is unchanged, and the next pass sees the new event
		InMemorySessionRepository store = InMemorySessionRepository.builder().build();
		AtomicBoolean appendOnRead = new AtomicBoolean();
		SessionService service = DefaultSessionService.builder().sessionRepository(new SessionRepository() {
			@Override
			public Session save(Session session) {
				return store.save(session);
			}

			@Override
			public boolean saveIfAbsent(Session session) {
				return store.saveIfAbsent(session);
			}

			@Override
			public @Nullable Session findById(String sessionId) {
				return store.findById(sessionId);
			}

			@Override
			public List<Session> findByIndex(String name, String value) {
				return store.findByIndex(name, value);
			}

			@Override
			public void delete(String sessionId) {
				store.delete(sessionId);
			}

			@Override
			public int deleteExpiredSessions(Instant before) {
				return store.deleteExpiredSessions(before);
			}

			@Override
			public void appendEvent(SessionEvent event) {
				store.appendEvent(event);
			}

			@Override
			public boolean appendEvents(String sessionId, List<SessionEvent> events, long expectedVersion) {
				return store.appendEvents(sessionId, events, expectedVersion);
			}

			@Override
			public long getEventVersion(String sessionId) {
				return store.getEventVersion(sessionId);
			}

			@Override
			public List<SessionEvent> findEvents(String sessionId, EventFilter filter) {
				List<SessionEvent> events = store.findEvents(sessionId, filter);
				if (appendOnRead.compareAndSet(true, false)) {
					store.appendEvent(
							SessionEvent.builder().sessionId(sessionId).message(new UserMessage("late")).build());
				}
				return events;
			}
		}).build();
		Session session = service.create(CreateSessionRequest.builder().build());
		for (int i = 1; i <= 4; i++) {
			service.appendMessage(session.id(), new UserMessage("msg-" + i));
		}
		CompactionStrategy window = WindowCompactionStrategy.builder().lastEvents(2).build();

		appendOnRead.set(true);
		CompactionResult overtaken = service.compact(session.id(), req -> true, window);
		CompactionResult applied = service.compact(session.id(), req -> true, window);

		assertThat(overtaken.isUnchanged()).isTrue();
		assertThat(applied.coveredEventCount()).isEqualTo(3);
		assertThat(service.getEvents(session.id()).stream().filter(e -> EventType.COMPACTION.equals(e.getType())))
			.hasSize(1);
		assertThat(service.getActiveMessages(session.id())).extracting(Message::getText)
			.containsExactly("msg-4", "late");
	}

	// --- deleteExpiredSessions ---

	@Test
	void deleteExpiredSessionsRemovesOnlyExpiredSessions() {
		Session active = this.service.create(CreateSessionRequest.builder()
			.metadata(SessionAttributes.PRINCIPAL_NAME, "u1")
			.timeToLive(java.time.Duration.ofHours(1))
			.build());
		Session expired = this.service.create(CreateSessionRequest.builder()
			.metadata(SessionAttributes.PRINCIPAL_NAME, "u2")
			.timeToLive(java.time.Duration.ofSeconds(-1)) // already in the past
			.build());

		int deleted = this.service.deleteExpiredSessions(Instant.now());

		assertThat(deleted).isEqualTo(1);
		assertThat(this.service.findById(active.id())).isNotNull();
		assertThat(this.service.findById(expired.id())).isNull();
	}

	@Test
	void deleteExpiredSessionsReturnsZeroWhenNoneExpired() {
		this.service.create(CreateSessionRequest.builder()
			.metadata(SessionAttributes.PRINCIPAL_NAME, "u1")
			.timeToLive(java.time.Duration.ofHours(1))
			.build());

		int deleted = this.service.deleteExpiredSessions(Instant.now());

		assertThat(deleted).isZero();
	}

	@Test
	void deleteExpiredSessionsReturnsZeroWhenNoSessions() {
		assertThat(this.service.deleteExpiredSessions(Instant.now())).isZero();
	}

}
