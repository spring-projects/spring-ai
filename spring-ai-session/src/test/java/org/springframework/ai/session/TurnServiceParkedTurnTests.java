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
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.TurnServiceTests.ContendedRepository;
import org.springframework.ai.session.compaction.CompactionBoundary;
import org.springframework.ai.session.compaction.WindowCompactionStrategy;
import org.springframework.ai.session.question.Answer;
import org.springframework.ai.session.question.Question;
import org.springframework.ai.session.status.SessionStatus;
import org.springframework.ai.session.status.StopReason;
import org.springframework.ai.session.status.TurnInProgressException;
import org.springframework.ai.session.status.TurnParkedException;
import org.springframework.ai.session.status.TurnService;
import org.springframework.ai.session.status.TurnStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Tests for the parked turn: {@link TurnService#park}, {@link TurnService#resume} and
 * {@link TurnService#pendingQuestion}.
 *
 * @author Christian Tzolov
 */
class TurnServiceParkedTurnTests {

	private static final Question QUESTION = new Question(Question.KIND_TOOL_CONFIRMATION,
			Map.of("toolCalls", List.of(Map.of("key", "c1", "name", "bookFlight", "awaitingDecision", true))));

	private static final List<Map<String, Object>> DECISIONS = List.of(Map.of("key", "c1", "allowed", true));

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
	void parkRecordsTheQuestionAndParksTheTurnInOneAppend() {
		runningTurnWithAPausedReply();
		int writes = this.repository.casWrites.get();

		TurnStatus parked = this.turns.park(this.sessionId, "run-1", QUESTION);

		assertThat(parked.status()).isEqualTo(SessionStatus.IDLE);
		assertThat(parked.stopReason()).isEqualTo(StopReason.REQUIRES_ACTION);
		assertThat(parked.isParked()).isTrue();
		assertThat(parked.runId()).isEqualTo("run-1");
		assertThat(parked.expiresAt()).isNull();
		assertThat(this.turns.status(this.sessionId)).isEqualTo(parked);
		assertThat(this.repository.casWrites).hasValue(writes + 1);
		// The question event comes right before the status event that parks the turn
		List<SessionEvent> log = this.service.getEvents(this.sessionId);
		SessionEvent asked = log.get(log.size() - 2);
		assertThat(asked.getType()).isEqualTo(EventType.QUESTION);
		assertThat(Question.from(asked)).isEqualTo(QUESTION);
		assertThat(log.get(log.size() - 1).getId()).isEqualTo(parked.eventId());
		assertThat(this.turns.pendingQuestion(this.sessionId)).isEqualTo(asked);
	}

	@Test
	void startOnAParkedSessionThrows() {
		runningTurnWithAPausedReply();
		TurnStatus parked = this.turns.park(this.sessionId, "run-1", QUESTION);

		assertThatExceptionOfType(TurnParkedException.class).isThrownBy(() -> this.turns.start(this.sessionId, "run-2"))
			.withMessageContaining(this.sessionId)
			.satisfies(ex -> assertThat(ex.getSessionId()).isEqualTo(this.sessionId));
		assertThat(this.turns.status(this.sessionId)).isEqualTo(parked);
		assertThat(this.turns.pendingQuestion(this.sessionId)).isNotNull();
		// An interrupt records the request and leaves the pause as it is: nothing runs
		assertThat(this.turns.interrupt(this.sessionId, "stop").isParked()).isTrue();
	}

	@Test
	void parkWritesNothingWhenTheTurnDoesNotRunUnderTheRunId() {
		// No turn at all
		TurnStatus idle = this.turns.park(this.sessionId, "run-1", QUESTION);
		assertThat(idle).isEqualTo(TurnStatus.idle());
		assertThat(this.service.getEvents(this.sessionId)).isEmpty();
		// A turn under another run
		this.turns.start(this.sessionId, "other");
		TurnStatus running = this.turns.park(this.sessionId, "run-1", QUESTION);
		assertThat(running.isRunning("other")).isTrue();
		assertThat(questionEvents()).isEmpty();
		assertThat(this.turns.pendingQuestion(this.sessionId)).isNull();
	}

	@Test
	void parkAfterAnInterruptLeavesTheInterruptStanding() {
		this.turns.start(this.sessionId, "run-1");
		this.turns.interrupt(this.sessionId, "user pressed stop");

		TurnStatus status = this.turns.park(this.sessionId, "run-1", QUESTION);

		assertThat(status.stopReason()).isEqualTo(StopReason.INTERRUPTED);
		assertThat(status.isParked()).isFalse();
		assertThat(questionEvents()).isEmpty();
	}

	@Test
	void parkThatKeepsLosingThrows() {
		this.turns.start(this.sessionId, "run-1");
		this.repository.onVersionRead(5, () -> this.store.appendEvent(message("meanwhile")));

		assertThatIllegalStateException().isThrownBy(() -> this.turns.park(this.sessionId, "run-1", QUESTION))
			.withMessageContaining("kept changing");
		assertThat(this.turns.status(this.sessionId).isRunning("run-1")).isTrue();
		assertThat(questionEvents()).isEmpty();
	}

	@Test
	void resumeAnswersTheQuestionAndStartsTheTurnInOneAppend() {
		runningTurnWithAPausedReply();
		this.turns.park(this.sessionId, "run-1", QUESTION);
		String questionId = this.turns.pendingQuestion(this.sessionId).getId();
		int writes = this.repository.casWrites.get();
		Instant before = Instant.now();

		TurnStatus resumed = this.turns.resume(this.sessionId, "run-2", DECISIONS, Duration.ofMinutes(5));

		assertThat(resumed.isRunning("run-2")).isTrue();
		assertThat(resumed.expiresAt()).isNotNull()
			.isBetween(before.plus(Duration.ofMinutes(5)), Instant.now().plus(Duration.ofMinutes(5)));
		assertThat(this.turns.status(this.sessionId)).isEqualTo(resumed);
		assertThat(this.repository.casWrites).hasValue(writes + 1);
		// The answer comes right before the status event that starts the turn again
		List<SessionEvent> log = this.service.getEvents(this.sessionId);
		SessionEvent answered = log.get(log.size() - 2);
		assertThat(answered.getType()).isEqualTo(EventType.ANSWER);
		assertThat(Answer.from(answered)).isEqualTo(new Answer(questionId, DECISIONS));
		assertThat(log.get(log.size() - 1).getId()).isEqualTo(resumed.eventId());
		assertThat(this.turns.pendingQuestion(this.sessionId)).isNull();
		// The turn runs as any other: it ends, and a new one starts
		assertThat(this.turns.end(this.sessionId, "run-2", StopReason.END_TURN).stopReason())
			.isEqualTo(StopReason.END_TURN);
		assertThat(this.turns.start(this.sessionId, "run-3").isRunning("run-3")).isTrue();
	}

	@Test
	void resumeMayKeepTheRunIdAndTakeNoLease() {
		this.turns.park(this.sessionId, this.turns.start(this.sessionId, "run-1").runId(), QUESTION);

		TurnStatus resumed = this.turns.resume(this.sessionId, "run-1", null);

		assertThat(resumed.isRunning("run-1")).isTrue();
		assertThat(resumed.expiresAt()).isNull();
		assertThat(Answer.from(answerEvents().get(0)).value()).isNull();
		// The log tells the two halves of the run apart by the parked status between them
		assertThat(statusEvents()).extracting(TurnStatus::from)
			.extracting(TurnStatus::status)
			.containsExactly(SessionStatus.RUNNING, SessionStatus.IDLE, SessionStatus.RUNNING);
	}

	@Test
	void resumeGeneratesARunIdWhenNoneIsGiven() {
		this.turns.park(this.sessionId, this.turns.start(this.sessionId, "run-1").runId(), QUESTION);

		assertThat(this.turns.resume(this.sessionId, null, "ok").runId()).isNotBlank().isNotEqualTo("run-1");
	}

	@Test
	void resumeRejectsAZeroOrNegativeTimeout() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.turns.resume(this.sessionId, "run-1", null, Duration.ZERO));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.turns.resume(this.sessionId, "run-1", null, Duration.ofSeconds(-1)));
	}

	@Test
	void resumeOnASessionThatIsNotParkedIsRefused() {
		// Never parked
		assertThatIllegalStateException().isThrownBy(() -> this.turns.resume(this.sessionId, "run-1", "ok"))
			.withMessageContaining("not parked");
		// Ended, not parked
		this.turns.start(this.sessionId, "run-1");
		this.turns.end(this.sessionId, "run-1", StopReason.END_TURN);
		assertThatIllegalStateException().isThrownBy(() -> this.turns.resume(this.sessionId, "run-2", "ok"))
			.withMessageContaining("not parked");
		// Running: the busy exception, as for a start
		this.turns.start(this.sessionId, "run-2");
		assertThatExceptionOfType(TurnInProgressException.class)
			.isThrownBy(() -> this.turns.resume(this.sessionId, "run-3", "ok"))
			.satisfies(ex -> assertThat(ex.getRunId()).isEqualTo("run-2"));
		assertThat(answerEvents()).isEmpty();
	}

	@Test
	void secondResumeWithTheSameAnswerIsRefusedAndRecordsNothing() {
		this.turns.park(this.sessionId, this.turns.start(this.sessionId, "run-1").runId(), QUESTION);
		this.turns.resume(this.sessionId, "run-2", DECISIONS);

		assertThatExceptionOfType(TurnInProgressException.class)
			.isThrownBy(() -> this.turns.resume(this.sessionId, "run-2", DECISIONS));
		this.turns.end(this.sessionId, "run-2", StopReason.END_TURN);
		assertThatIllegalStateException().isThrownBy(() -> this.turns.resume(this.sessionId, "run-2", DECISIONS));
		assertThat(answerEvents()).hasSize(1);
	}

	@Test
	void twoResumesLeaveOneWinner() {
		this.turns.park(this.sessionId, this.turns.start(this.sessionId, "run-1").runId(), QUESTION);
		// Another node resumes between this node's version read and its write
		this.repository.onVersionRead(1, () -> this.turns.resume(this.sessionId, "fast", "ok"));

		assertThatExceptionOfType(TurnInProgressException.class)
			.isThrownBy(() -> this.turns.resume(this.sessionId, "slow", "late"))
			.satisfies(ex -> assertThat(ex.getRunId()).isEqualTo("fast"));
		assertThat(this.turns.status(this.sessionId).isRunning("fast")).isTrue();
		assertThat(answerEvents()).singleElement()
			.satisfies(event -> assertThat(Answer.from(event).value()).isEqualTo("ok"));
	}

	@Test
	void resumeThatLosesToAnAppendRetriesAndWins() {
		this.turns.park(this.sessionId, this.turns.start(this.sessionId, "run-1").runId(), QUESTION);
		int writes = this.repository.casWrites.get();
		this.repository.onVersionRead(1, () -> this.store.appendEvent(message("meanwhile")));

		assertThat(this.turns.resume(this.sessionId, "run-2", "ok").isRunning("run-2")).isTrue();
		assertThat(this.repository.casWrites).hasValue(writes + 2);
		assertThat(answerEvents()).hasSize(1);
	}

	@Test
	void questionAndAnswerEventsAreNeverInTheActiveViewAndNeverCovered() {
		runningTurnWithAPausedReply();
		this.turns.park(this.sessionId, "run-1", QUESTION);
		this.turns.resume(this.sessionId, "run-1", DECISIONS);
		this.service.appendMessage(this.sessionId,
				ToolResponseMessage.builder()
					.responses(List.of(new ToolResponseMessage.ToolResponse("c1", "bookFlight", "Booked.")))
					.build());
		this.service.appendMessage(this.sessionId, new AssistantMessage("Your flight is booked."));
		this.turns.end(this.sessionId, "run-1", StopReason.END_TURN);
		this.turns.start(this.sessionId, "run-2");
		this.service.appendMessage(this.sessionId, new UserMessage("Thanks"));
		this.service.appendMessage(this.sessionId, new AssistantMessage("You are welcome."));
		this.turns.end(this.sessionId, "run-2", StopReason.END_TURN);

		this.service.compact(this.sessionId, request -> true, WindowCompactionStrategy.builder().lastTurns(1).build());

		assertThat(this.service.getActiveMessages(this.sessionId)).extracting(Message::getText)
			.containsExactly("Thanks", "You are welcome.");
		assertThat(this.service.getEvents(this.sessionId, EventFilter.active()))
			.allMatch(event -> EventType.MESSAGE.equals(event.getType()));
		List<SessionEvent> log = this.service.getEvents(this.sessionId);
		List<String> nonMessageIds = log.stream()
			.filter(event -> !EventType.MESSAGE.equals(event.getType()))
			.map(SessionEvent::getId)
			.toList();
		CompactionBoundary boundary = CompactionBoundary.newest(log);
		assertThat(boundary).isNotNull();
		assertThat(boundary.pinnedEventIds()).doesNotContainAnyElementsOf(nonMessageIds);
		assertThat(boundary.preservedEventIds()).doesNotContainAnyElementsOf(nonMessageIds);
		// The question is still answered after the compaction, and the status still reads
		assertThat(this.turns.pendingQuestion(this.sessionId)).isNull();
		assertThat(this.turns.status(this.sessionId).stopReason()).isEqualTo(StopReason.END_TURN);
	}

	/**
	 * A running turn whose reply asked for a tool call that waits for a decision; returns
	 * the reply's event id.
	 */
	private String runningTurnWithAPausedReply() {
		this.turns.start(this.sessionId, "run-1");
		this.service.appendMessage(this.sessionId, new UserMessage("Book VX205"));
		this.service.appendMessage(this.sessionId, AssistantMessage.builder()
			.content("")
			.toolCalls(List.of(new AssistantMessage.ToolCall("c1", "function", "bookFlight", "{\"flight\":\"VX205\"}")))
			.build());
		List<SessionEvent> log = this.service.getEvents(this.sessionId);
		return log.get(log.size() - 1).getId();
	}

	private SessionEvent message(String text) {
		return SessionEvent.builder().sessionId(this.sessionId).message(new UserMessage(text)).build();
	}

	private List<SessionEvent> questionEvents() {
		return events(EventType.QUESTION);
	}

	private List<SessionEvent> answerEvents() {
		return events(EventType.ANSWER);
	}

	private List<SessionEvent> statusEvents() {
		return events(EventType.STATUS);
	}

	private List<SessionEvent> events(EventType type) {
		return this.service.getEvents(this.sessionId).stream().filter(event -> type.equals(event.getType())).toList();
	}

}
