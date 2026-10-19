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

package org.springframework.ai.test.session;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.part.OpaquePayload;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.messages.part.ToolResultPart;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallDecision;
import org.springframework.ai.model.tool.ToolCallDecisions;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.EventType;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.compaction.CompactionBoundary;
import org.springframework.ai.session.question.Answer;
import org.springframework.ai.session.question.ToolConfirmationQuestion;
import org.springframework.ai.session.status.SessionStatus;
import org.springframework.ai.session.status.SessionStatusEvents;
import org.springframework.ai.session.status.StopReason;
import org.springframework.ai.session.status.TurnInProgressException;
import org.springframework.ai.session.status.TurnParkedException;
import org.springframework.ai.session.status.TurnService;
import org.springframework.ai.session.status.TurnStatus;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contract tests every {@link SessionRepository} implementation must pass. Subclass it,
 * return your repository from {@link #createRepository()}, and run the tests with the
 * JUnit Platform:
 *
 * <pre>{@code
 * class RedisSessionRepositoryContractTests extends AbstractSessionRepositoryContractTests {
 *
 *     &#64;Override
 *     protected SessionRepository createRepository() {
 *         return RedisSessionRepository.builder().connectionFactory(...).build();
 *     }
 *
 * }
 * }</pre>
 *
 * <p>
 * Every test creates its own sessions with random ids, so {@link #createRepository()} may
 * return a shared store. The tests cover session lifecycle, idempotent append, the read
 * contract of {@link SessionRepository#findEvents} (pushdown or
 * {@link EventFilter#apply(List)} must give the same result, including a {@code lastN}
 * window that keeps turns whole), cross-session reads, the atomic
 * {@link SessionRepository#appendEvents} and the active view a boundary defines. They
 * also run the {@link TurnService} on the store, since the turn status is read by event
 * type and written with the version-checked append: start, end, interrupt, the takeover
 * of an expired lease, a parked turn's question and answer, and concurrent starts.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public abstract class AbstractSessionRepositoryContractTests {

	protected SessionRepository repository;

	/**
	 * Returns the repository under test. Called before every test.
	 * @return the repository
	 */
	protected abstract SessionRepository createRepository();

	@BeforeEach
	void setUpRepository() {
		this.repository = createRepository();
	}

	// --- Sessions ---

	@Test
	void saveAndFindByIdRoundTrip() {
		Session session = newSession("user-1");

		Session found = findSession(session.id());

		assertThat(found.id()).isEqualTo(session.id());
		assertThat(SessionAttributes.principalName(found)).isEqualTo("user-1");
	}

	@Test
	void findByIdReturnsNullForUnknownSession() {
		assertThat(this.repository.findById(UUID.randomUUID().toString())).isNull();
	}

	@Test
	void findByMetadataReturnsSessionsWithThatAttributeValue() {
		String alice = "alice-" + UUID.randomUUID();
		String bob = "bob-" + UUID.randomUUID();
		Instant t0 = Instant.parse("2026-02-01T00:00:00Z");
		// Saved newest first: the lookup orders by creation time, then id
		Session second = this.repository.save(Session.builder()
			.id(UUID.randomUUID().toString())
			.createdAt(t0.plusSeconds(60))
			.metadata(SessionAttributes.PRINCIPAL_NAME, alice)
			.build());
		Session first = this.repository.save(Session.builder()
			.id(UUID.randomUUID().toString())
			.createdAt(t0)
			.metadata(SessionAttributes.PRINCIPAL_NAME, alice)
			.build());
		newSession(bob);

		assertThat(this.repository.findByMetadata(SessionAttributes.PRINCIPAL_NAME, alice)).extracting(Session::id)
			.containsExactly(first.id(), second.id());
		assertThat(this.repository.findByMetadata(SessionAttributes.PRINCIPAL_NAME, bob)).hasSize(1);
		assertThat(this.repository.findByMetadata(SessionAttributes.PRINCIPAL_NAME, "nobody-" + UUID.randomUUID()))
			.isEmpty();
		assertThat(this.repository.findByMetadata("no-such-attribute", alice)).isEmpty();
	}

	@Test
	void findByMetadataComparesValueAsString() {
		String id = UUID.randomUUID().toString();
		this.repository.save(Session.builder().id(id).metadata(SessionAttributes.PRINCIPAL_NAME, 4242L).build());

		assertThat(this.repository.findByMetadata(SessionAttributes.PRINCIPAL_NAME, "4242")).extracting(Session::id)
			.containsExactly(id);
	}

	@Test
	void longValuesSharingAPrefixAreToldApart() {
		// A store may index a prefix of the value; the match is on the whole of it
		String value = "p-" + UUID.randomUUID() + "-" + "x".repeat(600);
		Session shorter = newSession(value);
		Session longer = newSession(value + "tail");
		// A pair of chars that is one character, right where a 512-char prefix would cut
		String emoji = "e-" + UUID.randomUUID() + "-";
		emoji = emoji + "a".repeat(511 - emoji.length()) + "\uD83D\uDE00";
		Session cutInside = newSession(emoji + "tail");

		assertThat(this.repository.findByMetadata(SessionAttributes.PRINCIPAL_NAME, value)).extracting(Session::id)
			.containsExactly(shorter.id());
		assertThat(this.repository.findByMetadata(SessionAttributes.PRINCIPAL_NAME, value + "tail"))
			.extracting(Session::id)
			.containsExactly(longer.id());
		assertThat(this.repository.findByMetadata(SessionAttributes.PRINCIPAL_NAME, emoji + "tail"))
			.extracting(Session::id)
			.containsExactly(cutInside.id());
		assertThat(this.repository.findByMetadata(SessionAttributes.PRINCIPAL_NAME, emoji)).isEmpty();
	}

	@Test
	void sessionWithoutAttributeIsNotIndexed() {
		String id = UUID.randomUUID().toString();
		this.repository.save(Session.builder().id(id).metadata(Map.of("agent", "support")).build());

		assertThat(SessionAttributes.principalName(findSession(id))).isNull();
		assertThat(this.repository.findByMetadata(SessionAttributes.PRINCIPAL_NAME, "")).isEmpty();
	}

	@Test
	void saveIfAbsentNeverOverwritesExistingSession() {
		String id = UUID.randomUUID().toString();

		assertThat(this.repository.saveIfAbsent(principalSession(id, "alice"))).isTrue();
		assertThat(this.repository.saveIfAbsent(principalSession(id, "mallory"))).isFalse();

		assertThat(SessionAttributes.principalName(findSession(id))).isEqualTo("alice");
		assertThat(this.repository.findByMetadata(SessionAttributes.PRINCIPAL_NAME, "mallory")).extracting(Session::id)
			.doesNotContain(id);
	}

	@Test
	void concurrentSaveIfAbsentOfSameIdHasExactlyOneWinner() throws Exception {
		String id = UUID.randomUUID().toString();
		int threads = 8;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<Boolean>> results = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				String userId = "user-" + i;
				results.add(executor.submit(() -> {
					start.await();
					return this.repository.saveIfAbsent(principalSession(id, userId));
				}));
			}
			start.countDown();
			int winners = 0;
			for (Future<Boolean> result : results) {
				if (result.get(30, TimeUnit.SECONDS)) {
					winners++;
				}
			}
			assertThat(winners).isEqualTo(1);
			assertThat(this.repository.findById(id)).isNotNull();
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void saveOfExistingSessionKeepsCreatedAtAndEvents() {
		String id = UUID.randomUUID().toString();
		Instant created = Instant.parse("2026-01-01T00:00:00Z");
		this.repository.save(Session.builder()
			.id(id)
			.metadata(SessionAttributes.PRINCIPAL_NAME, "user-1")
			.createdAt(created)
			.build());
		append(id, user("kept"));

		Session updated = this.repository.save(Session.builder()
			.id(id)
			.createdAt(Instant.parse("2026-06-01T00:00:00Z"))
			.metadata(Map.of("key", "value", SessionAttributes.PRINCIPAL_NAME, "user-2"))
			.build());

		assertThat(updated.createdAt()).isEqualTo(created);
		Session found = findSession(id);
		assertThat(found.createdAt()).isEqualTo(created);
		assertThat(SessionAttributes.principalName(found)).isEqualTo("user-2");
		assertThat(found.metadata()).containsEntry("key", "value");
		assertThat(texts(id, EventFilter.all())).containsExactly("kept");
		// The index follows the metadata
		assertThat(this.repository.findByMetadata(SessionAttributes.PRINCIPAL_NAME, "user-1")).extracting(Session::id)
			.doesNotContain(id);
		assertThat(this.repository.findByMetadata(SessionAttributes.PRINCIPAL_NAME, "user-2")).extracting(Session::id)
			.contains(id);
	}

	@Test
	void deleteRemovesSessionAndItsEvents() {
		Session session = newSession("user-4");
		append(session.id(), user("gone"));

		this.repository.delete(session.id());

		assertThat(this.repository.findById(session.id())).isNull();
		assertThat(this.repository.findEvents(session.id(), EventFilter.all())).isEmpty();
		assertThat(this.repository.findByMetadata(SessionAttributes.PRINCIPAL_NAME, "user-4")).extracting(Session::id)
			.doesNotContain(session.id());
	}

	@Test
	void deleteExpiredSessionsDeletesOnlyExpiredSessionsAndTheirEvents() {
		Instant now = Instant.now();
		String userId = "user-ttl-" + UUID.randomUUID();
		String expired = UUID.randomUUID().toString();
		String extended = UUID.randomUUID().toString();
		String noTtl = UUID.randomUUID().toString();
		this.repository.save(Session.builder()
			.id(expired)
			.metadata(SessionAttributes.PRINCIPAL_NAME, userId)
			.expiresAt(now.minusSeconds(60))
			.build());
		this.repository.save(Session.builder()
			.id(extended)
			.metadata(SessionAttributes.PRINCIPAL_NAME, userId)
			.expiresAt(now.plusSeconds(60))
			.build());
		this.repository.save(principalSession(noTtl, userId));
		append(expired, user("gone"));
		append(extended, user("kept"));

		int deleted = this.repository.deleteExpiredSessions(now);

		assertThat(deleted).isGreaterThanOrEqualTo(1);
		assertThat(this.repository.findById(expired)).isNull();
		assertThat(this.repository.findEvents(expired, EventFilter.all())).isEmpty();
		assertThat(this.repository.findById(extended)).isNotNull();
		assertThat(this.repository.findById(noTtl)).isNotNull();
		assertThat(texts(extended, EventFilter.all())).containsExactly("kept");
	}

	@Test
	void sessionWithoutExpiryReadsBackWithoutOneAndIsNeverSweptAway() {
		Session session = this.repository.save(principalSession(UUID.randomUUID().toString(), "user-forever"));

		assertThat(findSession(session.id()).expiresAt()).isNull();

		this.repository.deleteExpiredSessions(Instant.now().plus(Duration.ofDays(365 * 100)));
		assertThat(findSession(session.id()).expiresAt()).isNull();
	}

	// --- Append ---

	@Test
	void appendedEventsAreReturnedInAppendOrder() {
		Session session = newSession("user-order");
		for (int i = 1; i <= 5; i++) {
			append(session.id(), user("msg-" + i));
		}

		assertThat(texts(session.id(), EventFilter.all())).containsExactly("msg-1", "msg-2", "msg-3", "msg-4", "msg-5");
	}

	@Test
	void appendOfSameEventIdIsIdempotentReplay() {
		Session session = newSession("user-replay");
		SessionEvent event = SessionEvent.builder()
			.id(UUID.randomUUID().toString())
			.sessionId(session.id())
			.message(new UserMessage("once"))
			.build();

		this.repository.appendEvent(event);
		long versionAfterFirst = this.repository.getEventVersion(session.id());
		this.repository.appendEvent(event);

		assertThat(texts(session.id(), EventFilter.all())).containsExactly("once");
		assertThat(this.repository.getEventVersion(session.id())).isEqualTo(versionAfterFirst);
	}

	@Test
	void appendWithEventIdOfAnotherSessionIsNeverSilentlyDropped() {
		// A store with a global id namespace (JDBC) rejects the clash with
		// IllegalStateException; a store keyed per session stores the event. Either way
		// the event must not be treated as a replay and dropped.
		Session owner = newSession("user-a");
		Session other = newSession("user-b");
		String eventId = UUID.randomUUID().toString();
		this.repository
			.appendEvent(SessionEvent.builder().id(eventId).sessionId(owner.id()).message(user("mine")).build());
		SessionEvent clash = SessionEvent.builder().id(eventId).sessionId(other.id()).message(user("theirs")).build();

		boolean rejected;
		try {
			this.repository.appendEvent(clash);
			rejected = false;
		}
		catch (IllegalStateException ex) {
			rejected = true;
		}

		assertThat(texts(owner.id(), EventFilter.all())).containsExactly("mine");
		if (rejected) {
			assertThat(this.repository.findEvents(other.id(), EventFilter.all())).isEmpty();
		}
		else {
			assertThat(texts(other.id(), EventFilter.all())).containsExactly("theirs");
		}
	}

	@Test
	void appendToUnknownSessionIsRejected() {
		SessionEvent event = SessionEvent.builder()
			.sessionId(UUID.randomUUID().toString())
			.message(user("orphan"))
			.build();

		assertThatThrownBy(() -> this.repository.appendEvent(event)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void eventVersionStartsAtZeroAndIncrementsPerNewEvent() {
		Session session = newSession("user-version");

		assertThat(this.repository.getEventVersion(session.id())).isZero();
		append(session.id(), user("a"));
		assertThat(this.repository.getEventVersion(session.id())).isEqualTo(1L);
		append(session.id(), user("b"));
		assertThat(this.repository.getEventVersion(session.id())).isEqualTo(2L);
	}

	// --- Reads ---

	@Test
	void findEventsForUnknownSessionReturnsEmptyList() {
		assertThat(this.repository.findEvents(UUID.randomUUID().toString(), EventFilter.all())).isEmpty();
	}

	@Test
	void messageTypesFilterKeepsOnlyThoseTypes() {
		Session session = newSession("user-types");
		append(session.id(), user("q"), assistant("a"), user("q2"));

		EventFilter filter = EventFilter.builder().messageTypes(Set.of(MessageType.ASSISTANT)).build();

		assertThat(texts(session.id(), filter)).containsExactly("a");
	}

	@Test
	void excludeSyntheticHidesSummaryEvents() {
		Session session = newSession("user-synthetic");
		append(session.id(), user("real"));
		this.repository.appendEvent(synthetic(session.id(), new AssistantMessage("summary")));

		assertThat(texts(session.id(), EventFilter.builder().excludeSynthetic(true).build())).containsExactly("real");
		assertThat(texts(session.id(), EventFilter.all())).containsExactly("real", "summary");
	}

	@Test
	void activeViewHidesCoveredEvents() {
		Session session = newSession("user-covered");
		append(session.id(), user("old"), user("new"));
		List<SessionEvent> log = this.repository.findEvents(session.id(), EventFilter.all());
		compact(session.id(), List.of(), List.of(), List.of(log.get(1)));

		assertThat(texts(session.id(), EventFilter.active())).containsExactly("new");
		assertThat(texts(session.id(), EventFilter.all())).containsExactly("old", "new");
	}

	@Test
	void lastNReturnsNewestEventsInChronologicalOrder() {
		Session session = newSession("user-lastn");
		for (int i = 1; i <= 5; i++) {
			append(session.id(), user("msg-" + i));
		}

		assertThat(texts(session.id(), EventFilter.lastN(2))).containsExactly("msg-4", "msg-5");
	}

	// --- Windows keep turns whole ---

	@Test
	void lastNNeverSplitsToolCallFromItsResults() {
		Session session = newSession("user-tool-window");
		append(session.id(), user("u1"), assistant("a1"), user("u2"), toolCall("call-1", "get_weather"),
				toolResponse("call-1", "get_weather", "22C"), assistant("a2"));

		// The cut lands on the tool result, the tool call, or the final reply: the whole
		// turn comes back every time, so a tool call is never returned without its result
		assertThat(labels(this.repository.findEvents(session.id(), EventFilter.lastN(1)))).containsExactly("u2",
				"A[get_weather]", "T[get_weather]", "a2");
		assertThat(labels(this.repository.findEvents(session.id(), EventFilter.lastN(2)))).containsExactly("u2",
				"A[get_weather]", "T[get_weather]", "a2");
		assertThat(labels(this.repository.findEvents(session.id(), EventFilter.lastN(3)))).containsExactly("u2",
				"A[get_weather]", "T[get_weather]", "a2");
		assertThat(labels(this.repository.findEvents(session.id(), EventFilter.lastN(4)))).containsExactly("u2",
				"A[get_weather]", "T[get_weather]", "a2");
		assertThat(labels(this.repository.findEvents(session.id(), EventFilter.lastN(5)))).containsExactly("u1", "a1",
				"u2", "A[get_weather]", "T[get_weather]", "a2");
	}

	@Test
	void lastNNeverSplitsSummaryPair() {
		Session session = newSession("user-summary-window");
		String id = session.id();
		summaryTurn(id, "summary").forEach(this.repository::appendEvent);
		append(id, user("u3"), assistant("a3"));

		assertThat(labels(this.repository.findEvents(id, EventFilter.lastN(3)))).containsExactly("Σ?", "Σ:summary",
				"u3", "a3");
	}

	@Test
	void lastNLandingOnAssistantExtendsToTurnStart() {
		Session session = newSession("user-assistant-cut");
		append(session.id(), user("u1"), assistant("a1"), user("u2"), assistant("a2"));

		assertThat(texts(session.id(), EventFilter.lastN(1))).containsExactly("u2", "a2");
		assertThat(texts(session.id(), EventFilter.lastN(2))).containsExactly("u2", "a2");
		assertThat(texts(session.id(), EventFilter.lastN(3))).containsExactly("u1", "a1", "u2", "a2");
	}

	@Test
	void lastNWithNoTurnStartBeforeCutIsPlain() {
		Session session = newSession("user-preamble");
		append(session.id(), system("s1"), system("s2"), user("u1"), assistant("a1"));

		// The cut lands in the preamble of stored system messages: nothing to extend to
		assertThat(texts(session.id(), EventFilter.lastN(3))).containsExactly("s2", "u1", "a1");
		// A filter that hides USER events has no turn start to extend to
		EventFilter assistantsOnly = EventFilter.builder().messageTypes(Set.of(MessageType.ASSISTANT)).lastN(1).build();
		assertThat(texts(session.id(), assistantsOnly)).containsExactly("a1");
	}

	// --- Compaction: an append-only log and a projected view ---

	@Test
	void appendEventsAppendsAtomicallyAndIncrementsVersionOnce() {
		Session session = newSession("user-append-events");
		append(session.id(), user("u1"), assistant("a1"));
		long version = this.repository.getEventVersion(session.id());

		boolean appended = this.repository.appendEvents(session.id(),
				List.of(event(session.id(), user("u2")), event(session.id(), assistant("a2"))), version);

		assertThat(appended).isTrue();
		assertThat(this.repository.getEventVersion(session.id())).isEqualTo(version + 1);
		assertThat(texts(session.id(), EventFilter.all())).containsExactly("u1", "a1", "u2", "a2");
	}

	@Test
	void appendEventsWithStaleVersionWritesNothing() {
		Session session = newSession("user-stale");
		append(session.id(), user("u1"));
		long version = this.repository.getEventVersion(session.id());

		boolean appended = this.repository.appendEvents(session.id(),
				List.of(event(session.id(), assistant("should-not-land"))), version - 1);

		assertThat(appended).isFalse();
		assertThat(this.repository.getEventVersion(session.id())).isEqualTo(version);
		assertThat(texts(session.id(), EventFilter.all())).containsExactly("u1");
	}

	@Test
	void appendEventsRejectsEventOfAnotherSessionAndDuplicateIdAndChangesNothing() {
		Session session = newSession("user-reject");
		Session other = newSession("user-reject");
		append(session.id(), user("u1"));
		long version = this.repository.getEventVersion(session.id());
		SessionEvent existing = this.repository.findEvents(session.id(), EventFilter.all()).get(0);

		assertThatThrownBy(() -> this.repository.appendEvents(session.id(),
				List.of(event(other.id(), user("wrong session"))), version))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> this.repository.appendEvents(session.id(), List.of(event(session.id(), user("fresh")),
				SessionEvent.builder().id(existing.getId()).sessionId(session.id()).message(user("dup")).build()),
				version))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(this.repository.getEventVersion(session.id())).isEqualTo(version);
		assertThat(texts(session.id(), EventFilter.all())).containsExactly("u1");
	}

	@Test
	void appendEventsRejectsDuplicateIdWithinBatchAndEmptyBatch() {
		Session session = newSession("user-batch-dup");
		append(session.id(), user("u1"));
		long version = this.repository.getEventVersion(session.id());
		SessionEvent fresh = event(session.id(), user("fresh"));
		SessionEvent twin = SessionEvent.builder()
			.id(fresh.getId())
			.sessionId(session.id())
			.message(user("twin"))
			.build();

		assertThatThrownBy(() -> this.repository.appendEvents(session.id(), List.of(fresh, twin), version))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> this.repository.appendEvents(session.id(), List.of(), version))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(this.repository.getEventVersion(session.id())).isEqualTo(version);
		assertThat(texts(session.id(), EventFilter.all())).containsExactly("u1");
	}

	@Test
	void appendEventsOnUnknownSessionIsRejected() {
		String unknown = UUID.randomUUID().toString();
		assertThatThrownBy(() -> this.repository.appendEvents(unknown, List.of(event(unknown, user("u1"))), 0L))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void compactedViewIsPinnedThenSummaryThenPreservedThenWhatFollows() {
		Session session = newSession("user-view");
		String id = session.id();
		append(id, user("u1"), system("rules"), assistant("a1"), user("u2"), assistant("a2"));
		List<SessionEvent> log = this.repository.findEvents(id, EventFilter.all());
		compact(id, List.of(log.get(1)), summaryTurn(id, "summary 1"), List.of(log.get(3), log.get(4)));
		append(id, user("u3"));

		assertThat(labels(this.repository.findEvents(id, EventFilter.active()))).containsExactly("rules", "Σ?",
				"Σ:summary 1", "u2", "a2", "u3");
		assertThat(labels(this.repository.findEvents(id, EventFilter.all()))).containsExactly("u1", "rules", "a1", "u2",
				"a2", "⊥", "Σ?", "Σ:summary 1", "u3");
	}

	@Test
	void laterBoundaryWinsAndLogOnlyGrows() {
		Session session = newSession("user-chain");
		String id = session.id();
		append(id, user("u1"), user("u2"));
		List<SessionEvent> log = this.repository.findEvents(id, EventFilter.all());
		compact(id, List.of(), summaryTurn(id, "summary 1"), List.of(log.get(1)));
		append(id, user("u3"), user("u4"));
		List<String> logAfterFirst = labels(this.repository.findEvents(id, EventFilter.all()));
		List<SessionEvent> view = this.repository.findEvents(id, EventFilter.active());
		// The second compaction covers summary 1, u2 and u3 and keeps u4
		compact(id, List.of(), summaryTurn(id, "summary 2"), List.of(view.get(view.size() - 1)));
		append(id, user("u5"));

		assertThat(labels(this.repository.findEvents(id, EventFilter.active()))).containsExactly("Σ?", "Σ:summary 2",
				"u4", "u5");
		assertThat(labels(this.repository.findEvents(id, EventFilter.all())))
			.startsWith(logAfterFirst.toArray(String[]::new))
			.endsWith("⊥", "Σ?", "Σ:summary 2", "u5");
	}

	@Test
	void pinnedEventOlderThanEarlierBoundaryIsStillInView() {
		Session session = newSession("user-pinned");
		String id = session.id();
		append(id, system("rules"), user("u1"), assistant("a1"));
		List<SessionEvent> log = this.repository.findEvents(id, EventFilter.all());
		SessionEvent rules = log.get(0);
		compact(id, List.of(rules), List.of(), List.of(log.get(1), log.get(2)));
		append(id, user("u2"), assistant("a2"));
		List<SessionEvent> view = this.repository.findEvents(id, EventFilter.active());
		compact(id, List.of(rules), List.of(), view.subList(view.size() - 2, view.size()));

		assertThat(labels(this.repository.findEvents(id, EventFilter.active()))).containsExactly("rules", "u2", "a2");
	}

	@Test
	void windowsAndCriteriaApplyToView() {
		Session session = newSession("user-view-window");
		String id = session.id();
		append(id, user("u1 secret"), assistant("a1"), user("u2"), assistant("a2"), user("u3"), assistant("a3"));
		List<SessionEvent> log = this.repository.findEvents(id, EventFilter.all());
		compact(id, List.of(), summaryTurn(id, "summary"), log.subList(2, 6));

		// lastN on the view keeps turns whole and never reaches into covered events
		assertThat(texts(id, EventFilter.active().merge(EventFilter.lastN(1)))).containsExactly("u3", "a3");
		assertThat(texts(id, EventFilter.active().merge(EventFilter.lastN(5))))
			.containsExactly("Summarize the conversation we had so far.", "summary", "u2", "a2", "u3", "a3");
		// a message-type filter on the view
		assertThat(texts(id, EventFilter.builder().activeView(true).messageTypes(Set.of(MessageType.USER)).build()))
			.containsExactly("Summarize the conversation we had so far.", "u2", "u3");
		// the covered event is still in the whole log
		assertThat(texts(id, EventFilter.all())).contains("u1 secret");
	}

	@Test
	void boundaryNamingIdsLogDoesNotHoldSkipsThem() {
		Session session = newSession("user-ghost-ids");
		String id = session.id();
		append(id, user("u1"), user("u2"));
		List<SessionEvent> log = this.repository.findEvents(id, EventFilter.all());
		// Named by the boundary, never written
		SessionEvent ghost = event(id, user("ghost"));
		compact(id, List.of(ghost), List.of(), List.of(log.get(1), ghost));
		append(id, user("u3"));

		assertThat(texts(id, EventFilter.active())).containsExactly("u2", "u3");
	}

	@Test
	void eventThatIsNotMessageIsInViewOnlyWhenItFollowsBoundary() {
		Session session = newSession("user-typed-view");
		String id = session.id();
		append(id, user("u1"));
		this.repository.appendEvent(status(id, "RUNNING"));
		append(id, user("u2"));
		List<SessionEvent> log = this.repository.findEvents(id, EventFilter.all());
		// The service builds the boundary from the conversation: it names u2 only
		compact(id, List.of(), List.of(), List.of(log.get(2)));
		this.repository.appendEvent(status(id, "IDLE"));

		EventFilter statuses = EventFilter.builder().eventTypes(Set.of(EventType.of("STATUS"))).build();
		assertThat(this.repository.findEvents(id, statuses)).extracting(e -> e.getPayload().get("status"))
			.containsExactly("RUNNING", "IDLE");
		assertThat(this.repository.findEvents(id, statuses.merge(EventFilter.builder().activeView(true).build())))
			.extracting(e -> e.getPayload().get("status"))
			.containsExactly("IDLE");
		// The boundary itself is not part of the view it defines
		assertThat(labels(this.repository.findEvents(id, EventFilter.builder().activeView(true).build())))
			.containsExactly("u2", "IDLE");
	}

	// --- Typed events ---

	@Test
	void eventThatIsNotMessageRoundTripsItsTypeAndPayload() {
		Session session = newSession("user-typed");
		append(session.id(), user("hello"));
		this.repository.appendEvent(SessionEvent.builder()
			.sessionId(session.id())
			.type(EventType.of("STATUS"))
			.payload(Map.of("status", "IDLE", "stopReason", "END_TURN", "turn", 1))
			.build());

		List<SessionEvent> all = this.repository.findEvents(session.id(), EventFilter.all());
		assertThat(all).extracting(SessionEvent::getType).containsExactly(EventType.MESSAGE, EventType.of("STATUS"));
		SessionEvent status = all.get(1);
		assertThat(status.hasMessage()).isFalse();
		assertThat(status.getPayload()).containsEntry("status", "IDLE")
			.containsEntry("stopReason", "END_TURN")
			.containsEntry("turn", 1);
	}

	@Test
	void activeKeepsOnlyMessageEventsInLogOrderBeforeAnyBoundary() {
		Session session = newSession("user-active");
		append(session.id(), user("q1"));
		this.repository.appendEvent(SessionEvent.builder().sessionId(session.id()).type(EventType.of("USAGE")).build());
		append(session.id(), assistant("a1"));

		assertThat(texts(session.id(), EventFilter.active())).containsExactly("q1", "a1");
		assertThat(this.repository.findEvents(session.id(), EventFilter.all())).extracting(SessionEvent::getType)
			.containsExactly(EventType.MESSAGE, EventType.of("USAGE"), EventType.MESSAGE);
	}

	@Test
	void eventTypesFilterSelectsTypesAndMessageTypesSkipNonMessageEvents() {
		Session session = newSession("user-types");
		append(session.id(), user("hello"));
		this.repository.appendEvent(SessionEvent.builder()
			.sessionId(session.id())
			.type(EventType.of("STATUS"))
			.payload("status", "hello")
			.build());
		this.repository.appendEvent(SessionEvent.builder().sessionId(session.id()).type(EventType.of("USAGE")).build());

		assertThat(this.repository.findEvents(session.id(),
				EventFilter.builder().eventTypes(Set.of(EventType.of("STATUS"), EventType.of("USAGE"))).build()))
			.extracting(SessionEvent::getType)
			.containsExactly(EventType.of("STATUS"), EventType.of("USAGE"));
		assertThat(texts(session.id(), EventFilter.builder().messageTypes(Set.of(MessageType.USER)).build()))
			.containsExactly("hello");
	}

	@Test
	void messagePartsAndMetadataSurviveRoundTrip() {
		Session session = newSession("user-parts");
		Media image = Media.builder()
			.mimeType(MimeTypeUtils.IMAGE_PNG)
			.data(java.net.URI.create("https://example.com/cat.png"))
			.build();
		OpaquePayload signature = new OpaquePayload("anthropic", "signature", "sig-1");
		append(session.id(), UserMessage.builder().text("look").media(image).metadata(Map.of("source", "web")).build(),
				AssistantMessage.builder()
					.parts(List.of(new ReasoningPart("thinking", null, signature, Map.of()), TextPart.of("a cat")))
					.build(),
				ToolResponseMessage.builder()
					.results(List
						.of(new ToolResultPart("c1", "lookup", List.of(TextPart.of("boom")), true, null, Map.of())))
					.build());

		List<SessionEvent> events = this.repository.findEvents(session.id(), EventFilter.all());
		UserMessage user = (UserMessage) events.get(0).getMessage();
		assertThat(user.getText()).isEqualTo("look");
		assertThat(user.getMedia()).hasSize(1);
		assertThat(user.getMedia().get(0).getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_PNG);
		assertThat(user.getMetadata()).containsEntry("source", "web");
		AssistantMessage assistant = (AssistantMessage) events.get(1).getMessage();
		assertThat(assistant.getText()).isEqualTo("a cat");
		assertThat(assistant.getReasoning()).hasSize(1);
		assertThat(assistant.getReasoning().get(0).payload()).isEqualTo(signature);
		ToolResponseMessage tool = (ToolResponseMessage) events.get(2).getMessage();
		assertThat(tool.getResults()).hasSize(1);
		assertThat(tool.getResults().get(0).isError()).isTrue();
		assertThat(tool.getResponses().get(0).responseData()).isEqualTo("boom");
	}

	@Test
	void lastNExtendsPastNonMessageEventsToTurnStart() {
		Session session = newSession("user-window-typed");
		append(session.id(), user("u1"), assistant("a1"));
		this.repository
			.appendEvent(SessionEvent.builder().sessionId(session.id()).type(EventType.of("STATUS")).build());
		append(session.id(), user("u2"));
		this.repository.appendEvent(SessionEvent.builder().sessionId(session.id()).type(EventType.of("USAGE")).build());
		append(session.id(), assistant("a2"));

		List<SessionEvent> window = this.repository.findEvents(session.id(), EventFilter.lastN(1));
		assertThat(window).extracting(SessionEvent::getType)
			.containsExactly(EventType.MESSAGE, EventType.of("USAGE"), EventType.MESSAGE);
		assertThat(window.stream().filter(SessionEvent::hasMessage).map(e -> e.getMessage().getText()))
			.containsExactly("u2", "a2");
		assertThat(texts(session.id(), EventFilter.active().merge(EventFilter.lastN(1)))).containsExactly("u2", "a2");
	}

	@Test
	void applicationsOwnEventTypeGoesThroughStore() {
		Session session = newSession("user-custom-type");
		EventType progress = EventType.of("MY_APP.PROGRESS");
		append(session.id(), user("u1"));
		this.repository.appendEvent(SessionEvent.builder()
			.sessionId(session.id())
			.type(progress)
			.payload("step", 2)
			.payload("note", "halfway")
			.build());

		List<SessionEvent> stored = this.repository.findEvents(session.id(),
				EventFilter.builder().eventTypes(Set.of(progress)).build());
		assertThat(stored).hasSize(1);
		assertThat(stored.get(0).getType()).isEqualTo(progress);
		assertThat(stored.get(0).hasMessage()).isFalse();
		assertThat(stored.get(0).getPayload()).containsEntry("note", "halfway");
		assertThat(((Number) Objects.requireNonNull(stored.get(0).getPayload().get("step"))).intValue()).isEqualTo(2);
		// Never part of the conversation
		assertThat(this.repository.findEvents(session.id(), EventFilter.active())).hasSize(1);
	}

	@Test
	void idsAtColumnLimitAndWithSpecialCharactersRoundTrip() {
		String longId = "s".repeat(255);
		String oddId = "test-with:complex@chars#123/" + UUID.randomUUID();
		for (String id : List.of(longId, oddId)) {
			this.repository.save(principalSession(id, "user-ids"));
			String eventId = ("e-" + id + "-".repeat(255)).substring(0, 255);
			this.repository
				.appendEvent(SessionEvent.builder().id(eventId).sessionId(id).message(user("hi " + id)).build());

			assertThat(this.repository.findById(id)).isNotNull();
			assertThat(this.repository.findEvents(id, EventFilter.all())).singleElement()
				.satisfies(event -> assertThat(event.getId()).hasSize(255));
		}
		assertThat(this.repository.findByMetadata(SessionAttributes.PRINCIPAL_NAME, "user-ids")).hasSize(2);
	}

	@Test
	void largeLogKeepsAppendOrderAndWindows() {
		Session session = newSession("user-large");
		for (int i = 0; i < 100; i++) {
			append(session.id(), user("m-" + i));
		}
		List<SessionEvent> batch = new ArrayList<>();
		for (int i = 100; i < 150; i++) {
			batch.add(event(session.id(), user("m-" + i)));
		}
		assertThat(this.repository.appendEvents(session.id(), batch, 100)).isTrue();

		List<String> texts = texts(session.id(), EventFilter.all());
		assertThat(texts).hasSize(150);
		for (int i = 0; i < 150; i++) {
			assertThat(texts.get(i)).isEqualTo("m-" + i);
		}
		assertThat(texts(session.id(), EventFilter.lastN(7))).containsExactly("m-143", "m-144", "m-145", "m-146",
				"m-147", "m-148", "m-149");
		assertThat(this.repository.getEventVersion(session.id())).isEqualTo(101);
	}

	// --- Turn status: the turn service on this store ---

	@Test
	void turnStartsEndsAndIsInterruptedThroughTheStore() {
		TurnService turns = sessionService().turns();
		String id = newSession("user-turns").id();

		TurnStatus started = turns.start(id, "run-1", Duration.ofMinutes(30));
		append(id, user("u1"), assistant("a1"));

		TurnStatus running = turns.status(id);
		assertThat(running.isRunning("run-1")).isTrue();
		assertThat(running.eventId()).isEqualTo(started.eventId());
		assertThat(running.expiresAt()).isEqualTo(started.expiresAt());
		assertThatThrownBy(() -> turns.start(id, "run-2", null)).isInstanceOfSatisfying(TurnInProgressException.class,
				ex -> assertThat(ex.getRunId()).isEqualTo("run-1"));

		turns.end(id, "run-1", StopReason.END_TURN);
		TurnStatus ended = turns.status(id);
		assertThat(ended.status()).isEqualTo(SessionStatus.IDLE);
		assertThat(ended.runId()).isEqualTo("run-1");
		assertThat(ended.stopReason()).isEqualTo(StopReason.END_TURN);

		turns.start(id, "run-2", null);
		append(id, user("u2"));
		assertThat(turns.interrupt(id, "user pressed stop").stopReason()).isEqualTo(StopReason.INTERRUPTED);
		// The interrupted run's own end is a no-op: the interrupt stands
		assertThat(turns.end(id, "run-2", StopReason.END_TURN).stopReason()).isEqualTo(StopReason.INTERRUPTED);
		assertThat(turns.status(id).runId()).isEqualTo("run-2");
		assertThat(
				this.repository.findEvents(id, EventFilter.builder().eventTypes(Set.of(EventType.INTERRUPT)).build()))
			.singleElement()
			.satisfies(event -> assertThat(event.getPayload()).containsEntry(SessionStatusEvents.PAYLOAD_REASON,
					"user pressed stop"));
		// The status never reaches the view
		assertThat(texts(id, EventFilter.active())).containsExactly("u1", "a1", "u2");
	}

	@Test
	void turnWhoseLeaseRanOutIsTakenOverThroughTheStore() {
		TurnService turns = sessionService().turns();
		String id = newSession("user-lease").id();
		// A run that died an hour ago without ending its turn
		this.repository
			.appendEvent(SessionStatusEvents.running(id, "run-dead", Instant.now().minus(Duration.ofHours(1))));
		assertThat(turns.status(id).isExpired(Instant.now())).isTrue();

		turns.start(id, "run-2", Duration.ofMinutes(30));

		assertThat(turns.status(id).isRunning("run-2")).isTrue();
		List<TurnStatus> transitions = this.repository
			.findEvents(id, EventFilter.builder().eventTypes(Set.of(EventType.STATUS)).build())
			.stream()
			.map(TurnStatus::from)
			.toList();
		assertThat(transitions).extracting(TurnStatus::status)
			.containsExactly(SessionStatus.RUNNING, SessionStatus.IDLE, SessionStatus.RUNNING);
		assertThat(transitions.get(1).runId()).isEqualTo("run-dead");
		assertThat(transitions.get(1).stopReason()).isEqualTo(StopReason.EXPIRED);
		// The dead run's own end, should it come back, is a no-op
		assertThat(turns.end(id, "run-dead", StopReason.END_TURN).isRunning("run-2")).isTrue();
	}

	@Test
	void parkedTurnRoundTripsItsQuestionAndAnswerThroughTheStore() {
		TurnService turns = sessionService().turns();
		String id = newSession("user-parked").id();
		turns.start(id, "run-1", Duration.ofMinutes(30));
		append(id, user("Book flight VX205 and pay"));
		AssistantMessage reply = AssistantMessage.builder()
			.toolCalls(List.of(new AssistantMessage.ToolCall("c1", "function", "bookFlight", "{\"flight\":\"VX205\"}"),
					new AssistantMessage.ToolCall("c2", "function", "pay", "{\"amount\":120.5,\"currency\":\"EUR\"}")))
			.build();
		SessionEvent replyEvent = event(id, reply);
		this.repository.appendEvent(replyEvent);
		ToolConfirmationQuestion asked = ToolConfirmationQuestion.of(replyEvent.getId(), reply, List.of("c1", "c2"));

		assertThat(turns.park(id, "run-1", asked.toQuestion()).isParked()).isTrue();

		assertThat(turns.status(id).isParked()).isTrue();
		assertThatThrownBy(() -> turns.start(id, "run-2", null)).isInstanceOf(TurnParkedException.class);
		SessionEvent pending = Objects.requireNonNull(turns.pendingQuestion(id));
		assertThat(ToolConfirmationQuestion.from(pending)).isEqualTo(asked);

		ToolCallDecisions decisions = ToolCallDecisions.of(ToolCallDecision.allow("c1"),
				ToolCallDecision.deny("c2", "Too expensive"));
		TurnStatus resumed = turns.resume(id, "run-1", ToolConfirmationQuestion.answer(decisions),
				Duration.ofMinutes(30));

		assertThat(resumed.isRunning("run-1")).isTrue();
		assertThat(turns.status(id).isRunning("run-1")).isTrue();
		assertThat(turns.pendingQuestion(id)).isNull();
		Answer answer = Answer
			.from(this.repository.findEvents(id, EventFilter.builder().eventTypes(Set.of(EventType.ANSWER)).build())
				.get(0));
		assertThat(answer.questionEventId()).isEqualTo(pending.getId());
		assertThat(ToolConfirmationQuestion.decisions(answer)).isEqualTo(decisions);
		// The same answer sent twice resumes once
		assertThatThrownBy(() -> turns.resume(id, null, ToolConfirmationQuestion.answer(decisions)))
			.isInstanceOf(TurnInProgressException.class);

		turns.end(id, "run-1", StopReason.END_TURN);
		assertThat(labels(this.repository.findEvents(id, EventFilter.all()))).containsExactly("RUNNING",
				"Book flight VX205 and pay", "A[bookFlight]", "QUESTION", "IDLE", "ANSWER", "RUNNING", "IDLE");
		assertThat(this.repository.findEvents(id, EventFilter.active())).hasSize(2);
	}

	@Test
	void concurrentStartsOnOneSessionLeaveExactlyOneTurn() throws Exception {
		TurnService turns = sessionService().turns();
		String id = newSession("user-race").id();
		int threads = 8;
		ExecutorService executor = Executors.newFixedThreadPool(threads);
		try {
			CountDownLatch start = new CountDownLatch(1);
			List<Future<Boolean>> results = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				String runId = "run-" + i;
				results.add(executor.submit(() -> {
					start.await();
					try {
						turns.start(id, runId, null);
						return true;
					}
					catch (TurnInProgressException ex) {
						return false;
					}
				}));
			}
			start.countDown();
			int winners = 0;
			for (Future<Boolean> result : results) {
				if (result.get(30, TimeUnit.SECONDS)) {
					winners++;
				}
			}
			assertThat(winners).isEqualTo(1);
			assertThat(turns.status(id).isRunning()).isTrue();
			assertThat(
					this.repository.findEvents(id, EventFilter.builder().eventTypes(Set.of(EventType.STATUS)).build()))
				.hasSize(1);
		}
		finally {
			executor.shutdownNow();
		}
	}

	// --- Helpers ---

	/**
	 * Returns a session service on the repository under test, for the tests that run the
	 * turn service on the store.
	 * @return a new session service
	 */
	protected SessionService sessionService() {
		return DefaultSessionService.builder().sessionRepository(this.repository).build();
	}

	/** Saves a new session with a random id for the given principal. */
	protected Session newSession(String principalName) {
		return this.repository.save(principalSession(UUID.randomUUID().toString(), principalName));
	}

	/** The session with the given id, which must exist. */
	protected Session findSession(String id) {
		Session found = this.repository.findById(id);
		assertThat(found).as("session %s", id).isNotNull();
		return Objects.requireNonNull(found);
	}

	/** A session with the given id whose metadata names the principal. */
	protected static Session principalSession(String id, String principalName) {
		return Session.builder().id(id).metadata(SessionAttributes.PRINCIPAL_NAME, principalName).build();
	}

	/** Appends each message as its own event. */
	protected void append(String sessionId, Message... messages) {
		for (Message message : messages) {
			this.repository.appendEvent(SessionEvent.builder().sessionId(sessionId).message(message).build());
		}
	}

	/** The texts of the message events matching the filter, in the order returned. */
	protected List<String> texts(String sessionId, EventFilter filter) {
		return this.repository.findEvents(sessionId, filter)
			.stream()
			.filter(SessionEvent::hasMessage)
			.map(e -> e.getMessage().getText())
			.toList();
	}

	protected static SessionEvent event(String sessionId, Message message) {
		return SessionEvent.builder().sessionId(sessionId).message(message).build();
	}

	/** A status event, the simplest event that is not a message. */
	protected static SessionEvent status(String sessionId, String status) {
		return SessionEvent.builder()
			.sessionId(sessionId)
			.type(EventType.of("STATUS"))
			.payload("status", status)
			.build();
	}

	/**
	 * Records a compaction the way the service does: appends a boundary naming the
	 * pinned, summary and preserved events, followed by the summary events, under the
	 * current version.
	 */
	protected void compact(String sessionId, List<SessionEvent> pinned, List<SessionEvent> summary,
			List<SessionEvent> preserved) {
		String boundaryId = UUID.randomUUID().toString();
		CompactionBoundary boundary = new CompactionBoundary(boundaryId, ids(pinned), ids(summary), ids(preserved), 0,
				0, "test");
		List<SessionEvent> events = new ArrayList<>();
		events.add(SessionEvent.builder()
			.id(boundaryId)
			.sessionId(sessionId)
			.type(EventType.COMPACTION)
			.payload(boundary.toPayload())
			.build());
		events.addAll(summary);
		assertThat(this.repository.appendEvents(sessionId, events, this.repository.getEventVersion(sessionId)))
			.isTrue();
	}

	private static List<String> ids(List<SessionEvent> events) {
		return events.stream().map(SessionEvent::getId).toList();
	}

	/** A synthetic event, as a compaction strategy would produce it. */
	protected static SessionEvent synthetic(String sessionId, Message message) {
		return SessionEvent.builder()
			.sessionId(sessionId)
			.message(message)
			.metadata(SessionEvent.METADATA_SYNTHETIC, true)
			.build();
	}

	/** A synthetic shadow-prompt + summary pair, as the summarizing strategy produces. */
	protected static List<SessionEvent> summaryTurn(String sessionId, String summary) {
		return List.of(synthetic(sessionId, new UserMessage("Summarize the conversation we had so far.")),
				synthetic(sessionId, new AssistantMessage(summary)));
	}

	/**
	 * Event labels: the text, "Σ?" for a synthetic shadow prompt, "Σ:text" for a summary,
	 * "A[name]" for an assistant tool call, "T[name]" for a tool result, "⊥" for a
	 * compaction boundary and the status (or the type name) for any other event.
	 */
	protected static List<String> labels(List<SessionEvent> events) {
		return events.stream().map(e -> {
			if (EventType.COMPACTION.equals(e.getType())) {
				return "⊥";
			}
			if (!e.hasMessage()) {
				return String.valueOf(e.getPayload().getOrDefault("status", e.getType().name()));
			}
			if (e.isSynthetic()) {
				return (e.getMessageType() == MessageType.USER) ? "Σ?" : "Σ:" + e.getMessage().getText();
			}
			if (e.getMessage() instanceof AssistantMessage assistant && assistant.hasToolCalls()) {
				return "A[" + assistant.getToolCalls().get(0).name() + "]";
			}
			if (e.getMessage() instanceof ToolResponseMessage responses) {
				return "T[" + responses.getResponses().get(0).name() + "]";
			}
			return e.getMessage().getText();
		}).toList();
	}

	/** The ids of the events with the given labels. */
	protected static Set<String> ids(List<SessionEvent> events, String... labels) {
		List<String> wanted = List.of(labels);
		List<String> eventLabels = labels(events);
		Set<String> ids = new LinkedHashSet<>();
		for (int i = 0; i < events.size(); i++) {
			if (wanted.contains(eventLabels.get(i))) {
				ids.add(events.get(i).getId());
			}
		}
		assertThat(ids).hasSize(labels.length);
		return ids;
	}

	protected static Message user(String text) {
		return new UserMessage(text);
	}

	protected static Message assistant(String text) {
		return new AssistantMessage(text);
	}

	protected static Message system(String text) {
		return new SystemMessage(text);
	}

	/** An assistant message that calls one tool. */
	protected static Message toolCall(String callId, String toolName) {
		return AssistantMessage.builder()
			.toolCalls(List.of(new AssistantMessage.ToolCall(callId, "function", toolName, "{}")))
			.build();
	}

	/** The result of one tool call. */
	protected static Message toolResponse(String callId, String toolName, String data) {
		return ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse(callId, toolName, data)))
			.build();
	}

	@Test
	void findByMetadataIsCaseSensitive() {
		String alice = "alice-" + UUID.randomUUID();
		Session lower = newSession(alice);
		Session upper = newSession(alice.toUpperCase(Locale.ROOT));

		assertThat(this.repository.findByMetadata(SessionAttributes.PRINCIPAL_NAME, alice)).extracting(Session::id)
			.containsExactly(lower.id());
		assertThat(this.repository.findByMetadata(SessionAttributes.PRINCIPAL_NAME, alice.toUpperCase(Locale.ROOT)))
			.extracting(Session::id)
			.containsExactly(upper.id());
	}

	@Test
	void eventKeepsItsIdTimestampAndMetadataThroughStore() {
		Session session = newSession("user-round-trip");
		Instant at = Instant.parse("2026-03-01T10:15:30.123456Z");
		this.repository.appendEvent(SessionEvent.builder()
			.id("event-" + session.id())
			.sessionId(session.id())
			.timestamp(at)
			.message(user("hello"))
			.metadata("model", "m-1")
			.metadata("latencyMs", 230)
			.build());

		SessionEvent stored = this.repository.findEvents(session.id(), EventFilter.all()).get(0);

		assertThat(stored.getId()).isEqualTo("event-" + session.id());
		assertThat(stored.getTimestamp()).isEqualTo(at);
		assertThat(stored.getMetadata()).containsEntry("model", "m-1").containsEntry("latencyMs", 230);
		assertThat(stored.isSynthetic()).isFalse();
	}

}
