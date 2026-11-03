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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.DefaultSessionServiceTests.ReadHookRepository;
import org.springframework.ai.session.compaction.CompactionBoundary;
import org.springframework.ai.session.compaction.WindowCompactionStrategy;
import org.springframework.ai.session.status.SessionStatus;
import org.springframework.ai.session.status.SessionStatusEvents;
import org.springframework.ai.session.status.StopReason;
import org.springframework.ai.session.status.TurnInProgressException;
import org.springframework.ai.session.status.TurnService;
import org.springframework.ai.session.status.TurnStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * The turn status of a session as {@link SessionService} projects it from the log:
 * {@link TurnService#status}, {@link TurnService#start}, {@link TurnService#end} and
 * {@link TurnService#interrupt}, on a {@link DefaultSessionService}.
 *
 * @author Christian Tzolov
 */
class TurnServiceTests {

	private final InMemorySessionRepository store = InMemorySessionRepository.builder().build();

	private final ContendedRepository repository = new ContendedRepository(this.store);

	private final SessionService service = DefaultSessionService.builder().sessionRepository(this.repository).build();

	private final TurnService turns = this.service.turns();

	private String sessionId;

	@BeforeEach
	void createSession() {
		this.sessionId = this.service.create(CreateSessionRequest.builder().build()).id();
	}

	@Test
	void freshSessionIsIdleWithoutAnEvent() {
		assertThat(this.turns.status(this.sessionId)).isEqualTo(TurnStatus.idle());
		assertThat(this.turns.status("no-such-session")).isEqualTo(TurnStatus.idle());
		assertThat(this.service.getEvents(this.sessionId)).isEmpty();
	}

	@Test
	void startRecordsRunningStatus() {
		TurnStatus started = this.turns.start(this.sessionId, "run-1");

		assertThat(started.status()).isEqualTo(SessionStatus.RUNNING);
		assertThat(started.runId()).isEqualTo("run-1");
		assertThat(started.stopReason()).isNull();
		assertThat(started.since()).isNotNull();
		assertThat(started.eventId()).isNotNull();
		assertThat(this.turns.status(this.sessionId)).isEqualTo(started);
		List<SessionEvent> log = this.service.getEvents(this.sessionId);
		assertThat(log).singleElement().satisfies(event -> {
			assertThat(event.getType()).isEqualTo(EventType.STATUS);
			assertThat(event.getId()).isEqualTo(started.eventId());
			assertThat(event.getTimestamp()).isEqualTo(started.since());
		});
		assertThat(this.service.getEvents(this.sessionId, EventFilter.active())).isEmpty();
		assertThat(this.repository.casWrites).hasValue(1);
	}

	@Test
	void startGeneratesARunIdWhenNoneIsGiven() {
		TurnStatus started = this.turns.start(this.sessionId, null);

		assertThat(started.runId()).isNotBlank();
		assertThat(this.turns.start(this.service.create(CreateSessionRequest.builder().build()).id(), " ").runId())
			.isNotBlank();
	}

	@Test
	void startWhileATurnRunsThrowsWithItsRunId() {
		this.turns.start(this.sessionId, "run-1");

		assertThatExceptionOfType(TurnInProgressException.class)
			.isThrownBy(() -> this.turns.start(this.sessionId, "run-2"))
			.withMessageContaining(this.sessionId)
			.withMessageContaining("run-1")
			.satisfies(ex -> {
				assertThat(ex.getSessionId()).isEqualTo(this.sessionId);
				assertThat(ex.getRunId()).isEqualTo("run-1");
			});
		assertThat(this.turns.status(this.sessionId).runId()).isEqualTo("run-1");
		assertThat(this.service.getEvents(this.sessionId)).hasSize(1);
	}

	@Test
	void startWithATimeoutRecordsTheLease() {
		Instant before = Instant.now();

		TurnStatus started = this.turns.start(this.sessionId, "run-1", Duration.ofMinutes(30));

		assertThat(started.isRunning("run-1")).isTrue();
		assertThat(started.expiresAt()).isNotNull()
			.isBetween(before.plus(Duration.ofMinutes(30)), Instant.now().plus(Duration.ofMinutes(30)));
		assertThat(started.isExpired(Instant.now())).isFalse();
		assertThat(started.isExpired(Instant.now().plus(Duration.ofMinutes(31)))).isTrue();
		assertThat(this.turns.status(this.sessionId)).isEqualTo(started);
	}

	@Test
	void startWithoutATimeoutNeverExpires() {
		TurnStatus started = this.turns.start(this.sessionId, "run-1", null);

		assertThat(started.expiresAt()).isNull();
		assertThat(started.isExpired(Instant.MAX)).isFalse();
		// Only an end or an interrupt frees the session
		assertThatExceptionOfType(TurnInProgressException.class)
			.isThrownBy(() -> this.turns.start(this.sessionId, "run-2", Duration.ofMinutes(30)));
	}

	@Test
	void startRejectsAZeroOrNegativeTimeout() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.turns.start(this.sessionId, "run-1", Duration.ZERO));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.turns.start(this.sessionId, "run-1", Duration.ofSeconds(-1)));
		assertThat(this.turns.status(this.sessionId)).isEqualTo(TurnStatus.idle());
	}

	@Test
	void startTakesOverATurnWhoseLeaseRanOut() {
		// The run died an hour ago without ending its turn
		this.store.appendEvent(
				SessionStatusEvents.running(this.sessionId, "run-dead", Instant.now().minus(Duration.ofHours(1))));
		long version = this.service.getEventVersion(this.sessionId);

		TurnStatus started = this.turns.start(this.sessionId, "run-2", Duration.ofMinutes(30));

		assertThat(started.isRunning("run-2")).isTrue();
		assertThat(this.turns.status(this.sessionId)).isEqualTo(started);
		// The old turn's end and the new start land in one version-checked append
		assertThat(this.service.getEventVersion(this.sessionId)).isEqualTo(version + 1);
		assertThat(this.repository.casWrites).hasValue(1);
		List<SessionEvent> events = this.service.getEvents(this.sessionId);
		assertThat(events).hasSize(3).allMatch(event -> EventType.STATUS.equals(event.getType()));
		TurnStatus expired = TurnStatus.from(events.get(1));
		assertThat(expired.status()).isEqualTo(SessionStatus.IDLE);
		assertThat(expired.runId()).isEqualTo("run-dead");
		assertThat(expired.stopReason()).isEqualTo(StopReason.EXPIRED);
		// Nobody asked the session to stop
		assertThat(events).noneMatch(event -> EventType.INTERRUPT.equals(event.getType()));
		// The dead run's own end, should it come back, is a no-op
		assertThat(this.turns.end(this.sessionId, "run-dead", StopReason.END_TURN)).isEqualTo(started);
	}

	@Test
	void startRefusesATurnWhoseLeaseStillRuns() {
		this.turns.start(this.sessionId, "run-1", Duration.ofMinutes(30));

		assertThatExceptionOfType(TurnInProgressException.class)
			.isThrownBy(() -> this.turns.start(this.sessionId, "run-2", Duration.ofMinutes(30)))
			.satisfies(ex -> assertThat(ex.getRunId()).isEqualTo("run-1"));
		assertThat(this.turns.status(this.sessionId).isRunning("run-1")).isTrue();
	}

	@Test
	void twoTakeoversOfAnExpiredTurnLeaveOneWinner() {
		this.store.appendEvent(
				SessionStatusEvents.running(this.sessionId, "run-dead", Instant.now().minus(Duration.ofHours(1))));
		// The other harness takes the session over between this one's version read and
		// its write: the write is refused, the new read sees a live turn
		this.repository.onVersionRead(1, () -> this.turns.start(this.sessionId, "other", Duration.ofMinutes(30)));

		assertThatExceptionOfType(TurnInProgressException.class)
			.isThrownBy(() -> this.turns.start(this.sessionId, "run-2", Duration.ofMinutes(30)))
			.satisfies(ex -> assertThat(ex.getRunId()).isEqualTo("other"));
		assertThat(this.turns.status(this.sessionId).isRunning("other")).isTrue();
		assertThat(this.service.getEvents(this.sessionId)).hasSize(3);
	}

	@Test
	void startOnAMissingSessionIsRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.turns.start("no-such-session", "run-1"));
	}

	@Test
	void endByItsOwnerRecordsTheStopReason() {
		this.turns.start(this.sessionId, "run-1");

		TurnStatus ended = this.turns.end(this.sessionId, "run-1", StopReason.END_TURN);

		assertThat(ended.status()).isEqualTo(SessionStatus.IDLE);
		assertThat(ended.runId()).isEqualTo("run-1");
		assertThat(ended.stopReason()).isEqualTo(StopReason.END_TURN);
		assertThat(this.turns.status(this.sessionId)).isEqualTo(ended);
		assertThat(this.service.getEvents(this.sessionId)).hasSize(2)
			.allMatch(event -> EventType.STATUS.equals(event.getType()));
		// The next turn starts
		assertThat(this.turns.start(this.sessionId, "run-2").isRunning("run-2")).isTrue();
	}

	@Test
	void endByAnotherRunIdIsANoOp() {
		TurnStatus running = this.turns.start(this.sessionId, "run-1");

		TurnStatus status = this.turns.end(this.sessionId, "run-2", StopReason.END_TURN);

		assertThat(status).isEqualTo(running);
		assertThat(this.service.getEvents(this.sessionId)).hasSize(1);
		assertThat(this.repository.casWrites).hasValue(1);
	}

	@Test
	void endAfterAnInterruptIsANoOp() {
		this.turns.start(this.sessionId, "run-1");
		TurnStatus interrupted = this.turns.interrupt(this.sessionId, "stop");

		TurnStatus status = this.turns.end(this.sessionId, "run-1", StopReason.END_TURN);

		assertThat(status).isEqualTo(interrupted);
		assertThat(status.stopReason()).isEqualTo(StopReason.INTERRUPTED);
		assertThat(this.service.getEvents(this.sessionId)).hasSize(3);
	}

	@Test
	void endOnAnIdleSessionIsANoOp() {
		assertThat(this.turns.end(this.sessionId, "run-1", StopReason.ERROR)).isEqualTo(TurnStatus.idle());
		assertThat(this.service.getEvents(this.sessionId)).isEmpty();
	}

	@Test
	void interruptWhileATurnRunsRecordsBothEventsUnderOneVersion() {
		this.turns.start(this.sessionId, "run-1");
		long version = this.service.getEventVersion(this.sessionId);

		TurnStatus status = this.turns.interrupt(this.sessionId, "user pressed stop");

		assertThat(status.status()).isEqualTo(SessionStatus.IDLE);
		assertThat(status.runId()).isEqualTo("run-1");
		assertThat(status.stopReason()).isEqualTo(StopReason.INTERRUPTED);
		assertThat(this.service.getEventVersion(this.sessionId)).isEqualTo(version + 1);
		List<SessionEvent> log = this.service.getEvents(this.sessionId);
		assertThat(log).hasSize(3);
		assertThat(log.get(1).getType()).isEqualTo(EventType.INTERRUPT);
		assertThat(SessionStatusEvents.reason(log.get(1))).isEqualTo("user pressed stop");
		assertThat(log.get(2).getType()).isEqualTo(EventType.STATUS);
		assertThat(log.get(2).getId()).isEqualTo(status.eventId());
		assertThat(this.turns.status(this.sessionId)).isEqualTo(status);
	}

	@Test
	void interruptWhileIdleRecordsTheRequestOnly() {
		this.turns.start(this.sessionId, "run-1");
		TurnStatus ended = this.turns.end(this.sessionId, "run-1", StopReason.END_TURN);

		TurnStatus status = this.turns.interrupt(this.sessionId, null);

		assertThat(status).isEqualTo(ended);
		List<SessionEvent> log = this.service.getEvents(this.sessionId);
		assertThat(log).hasSize(3);
		assertThat(log.get(2).getType()).isEqualTo(EventType.INTERRUPT);
		assertThat(log.get(2).getPayload()).isEmpty();
		assertThat(this.turns.status(this.sessionId)).isEqualTo(ended);
	}

	@Test
	void startThatLosesTheRaceRetriesAndWins() {
		// A message lands between the version read and the write: the first write is
		// refused, the second, after a new read, lands
		this.repository.onVersionRead(1, () -> this.store
			.appendEvent(SessionEvent.builder().sessionId(this.sessionId).message(new UserMessage("late")).build()));

		TurnStatus started = this.turns.start(this.sessionId, "run-1");

		assertThat(started.isRunning("run-1")).isTrue();
		assertThat(this.repository.casWrites).hasValue(2);
		assertThat(this.service.getEvents(this.sessionId)).hasSize(2);
		assertThat(this.turns.status(this.sessionId)).isEqualTo(started);
	}

	@Test
	void startThatLosesTheRaceToAnotherStartThrows() {
		// The other harness starts its turn between the version read and the write: the
		// write is refused, the new read sees the running turn
		this.repository.onVersionRead(1,
				() -> this.store.appendEvent(SessionStatusEvents.running(this.sessionId, "other")));

		assertThatExceptionOfType(TurnInProgressException.class)
			.isThrownBy(() -> this.turns.start(this.sessionId, "run-1"))
			.satisfies(ex -> assertThat(ex.getRunId()).isEqualTo("other"));
		assertThat(this.turns.status(this.sessionId).runId()).isEqualTo("other");
	}

	@Test
	void startThatKeepsLosingThrows() {
		// Message appends keep overtaking the write, none of them a turn: the busy
		// exception would lie, so the plain state exception is thrown like end's
		this.repository.onVersionRead(Integer.MAX_VALUE, () -> this.store
			.appendEvent(SessionEvent.builder().sessionId(this.sessionId).message(new UserMessage("late")).build()));

		assertThatIllegalStateException().isThrownBy(() -> this.turns.start(this.sessionId, "run-1"))
			.isNotInstanceOf(TurnInProgressException.class)
			.withMessageContaining("kept changing");
		assertThat(this.repository.casWrites).hasValue(5);
		assertThat(this.turns.status(this.sessionId)).isEqualTo(TurnStatus.idle());
		assertThat(this.service.getEvents(this.sessionId)).hasSize(5)
			.allMatch(event -> EventType.MESSAGE.equals(event.getType()));
	}

	@Test
	void startThatLosesFourTimesWinsOnTheFifth() {
		this.repository.onVersionRead(4, () -> this.store
			.appendEvent(SessionEvent.builder().sessionId(this.sessionId).message(new UserMessage("late")).build()));

		assertThat(this.turns.start(this.sessionId, "run-1").isRunning("run-1")).isTrue();
		assertThat(this.repository.casWrites).hasValue(5);
		assertThat(this.turns.status(this.sessionId).isRunning("run-1")).isTrue();
	}

	@Test
	void endThatKeepsLosingThrows() {
		this.turns.start(this.sessionId, "run-1");
		this.repository.onVersionRead(Integer.MAX_VALUE, () -> this.store
			.appendEvent(SessionEvent.builder().sessionId(this.sessionId).message(new UserMessage("late")).build()));

		assertThatIllegalStateException().isThrownBy(() -> this.turns.end(this.sessionId, "run-1", StopReason.END_TURN))
			.withMessageContaining("kept changing");
		assertThat(this.turns.status(this.sessionId).isRunning("run-1")).isTrue();
	}

	@Test
	void interruptThatLosesTheRaceToTheTurnsEndRecordsTheRequestOnly() {
		// The turn ends between the interrupt's read and its write: re-evaluated, the
		// interrupt finds no running turn and records the request alone
		this.turns.start(this.sessionId, "run-1");
		this.repository.onVersionRead(1,
				() -> this.store.appendEvent(SessionStatusEvents.idle(this.sessionId, "run-1", StopReason.END_TURN)));

		TurnStatus status = this.turns.interrupt(this.sessionId, "stop");

		assertThat(status.stopReason()).isEqualTo(StopReason.END_TURN);
		List<SessionEvent> log = this.service.getEvents(this.sessionId);
		assertThat(log).extracting(SessionEvent::getType)
			.containsExactly(EventType.STATUS, EventType.STATUS, EventType.INTERRUPT);
	}

	@Test
	void statusEventsAreNeverInTheActiveViewAndNeverCovered() {
		this.turns.start(this.sessionId, "run-1");
		this.service.appendMessage(this.sessionId, new UserMessage("q1"));
		this.service.appendMessage(this.sessionId, new AssistantMessage("a1"));
		this.turns.end(this.sessionId, "run-1", StopReason.END_TURN);
		this.turns.start(this.sessionId, "run-2");
		this.service.appendMessage(this.sessionId, new UserMessage("q2"));
		this.service.appendMessage(this.sessionId, new AssistantMessage("a2"));
		this.turns.interrupt(this.sessionId, null);

		this.service.compact(this.sessionId, request -> true, WindowCompactionStrategy.builder().lastTurns(1).build());

		assertThat(this.service.getActiveMessages(this.sessionId)).extracting(Message::getText)
			.containsExactly("q2", "a2");
		assertThat(this.service.getEvents(this.sessionId, EventFilter.active()))
			.allMatch(event -> EventType.MESSAGE.equals(event.getType()));
		List<SessionEvent> log = this.service.getEvents(this.sessionId);
		assertThat(log).filteredOn(event -> EventType.STATUS.equals(event.getType())).hasSize(4);
		assertThat(log).filteredOn(event -> EventType.INTERRUPT.equals(event.getType())).hasSize(1);
		CompactionBoundary boundary = CompactionBoundary.newest(log);
		assertThat(boundary).isNotNull();
		assertThat(boundary.coveredEventCount()).isEqualTo(2);
		List<String> statusIds = log.stream()
			.filter(event -> !EventType.MESSAGE.equals(event.getType()))
			.map(SessionEvent::getId)
			.toList();
		assertThat(boundary.pinnedEventIds()).doesNotContainAnyElementsOf(statusIds);
		assertThat(boundary.preservedEventIds()).doesNotContainAnyElementsOf(statusIds);
		// The status still reads after the compaction
		assertThat(this.turns.status(this.sessionId).stopReason()).isEqualTo(StopReason.INTERRUPTED);
	}

	@Test
	void appendEventsAppliesTheSystemMessageRule() {
		long version = this.service.getEventVersion(this.sessionId);
		SessionEvent system = SessionEvent.builder()
			.sessionId(this.sessionId)
			.message(new SystemMessage("Answer in French."))
			.build();

		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.service.appendEvents(this.sessionId, List.of(system), version))
			.withMessageContaining("allowSystemMessages(true)");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.service.appendEvents(this.sessionId, List.of(), version));
		assertThat(this.service.getEvents(this.sessionId)).isEmpty();
	}

	@Test
	void appendEventsIsTheVersionCheckedAppend() {
		long version = this.service.getEventVersion(this.sessionId);
		SessionEvent first = SessionEvent.builder().sessionId(this.sessionId).message(new UserMessage("a")).build();
		SessionEvent second = SessionEvent.builder().sessionId(this.sessionId).message(new UserMessage("b")).build();

		assertThat(this.service.appendEvents(this.sessionId, List.of(first), version)).isTrue();
		assertThat(this.service.appendEvents(this.sessionId, List.of(second), version)).isFalse();
		assertThat(this.service.getEventVersion(this.sessionId)).isEqualTo(version + 1);
		assertThat(this.service.getMessages(this.sessionId)).extracting(Message::getText).containsExactly("a");
	}

	/**
	 * A store that counts the version-checked writes and runs a hook after the version
	 * read, a given number of times, like a writer that slips in between the read and the
	 * write.
	 */
	static final class ContendedRepository extends ReadHookRepository {

		final AtomicInteger casWrites = new AtomicInteger();

		private @Nullable Runnable afterVersionRead;

		private int remaining;

		ContendedRepository(SessionRepository store) {
			super(store);
		}

		void onVersionRead(int times, Runnable hook) {
			this.afterVersionRead = hook;
			this.remaining = times;
		}

		@Override
		public long getEventVersion(String sessionId) {
			long version = super.getEventVersion(sessionId);
			if (this.afterVersionRead != null && this.remaining > 0) {
				this.remaining--;
				this.afterVersionRead.run();
			}
			return version;
		}

		@Override
		public boolean appendEvents(String sessionId, List<SessionEvent> events, long expectedVersion) {
			this.casWrites.incrementAndGet();
			return super.appendEvents(sessionId, events, expectedVersion);
		}

	}

}
