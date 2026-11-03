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

package org.springframework.ai.session.status;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.session.EventType;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.question.Question;
import org.springframework.ai.session.question.QuestionEvents;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * The turns of a session: starts, ends, interrupts, parks and resumes them, and reads
 * their status. Every harness runs its turns through the same methods, so a turn started
 * on one instance can be interrupted from another, and two harnesses never run one
 * session at once. {@link SessionService#turns()} returns it.
 * <p>
 * The service keeps no state of its own. The status is a projection of the session's log,
 * the newest {@link EventType#STATUS} event, and every transition is a version-checked
 * append through the {@link SessionService}: the version and the newest status are read,
 * the events are written against that version, and a write another append overtook is
 * re-evaluated after a fresh read, five times in all, and then given up with an
 * {@link IllegalStateException}. A decorated service, an authorizing one for example,
 * guards these reads and writes like any other.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 * @see TurnStatus
 */
public final class TurnService {

	private final SessionService sessionService;

	/**
	 * Creates the turn service on the session service, whose log holds the status.
	 * @param sessionService the session service the turns run on
	 */
	public TurnService(SessionService sessionService) {
		Assert.notNull(sessionService, "sessionService must not be null");
		this.sessionService = sessionService;
	}

	/**
	 * Returns the turn status of the session: whether a turn runs on it, under which run
	 * id, and why the last one ended. A session without a {@link EventType#STATUS} event,
	 * because no harness has run a turn on it yet or because it does not exist, is
	 * {@link TurnStatus#idle()}. One indexed read.
	 */
	public TurnStatus status(String sessionId) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		List<SessionEvent> newest = this.sessionService.getEvents(sessionId, SessionStatusEvents.NEWEST_STATUS);
		return newest.isEmpty() ? TurnStatus.idle() : TurnStatus.from(newest.get(newest.size() - 1));
	}

	/**
	 * Starts a turn on the session without a lease: it runs until {@link #end} or
	 * {@link #interrupt} ends it, however long that takes, and a harness that dies in
	 * between leaves the session busy until someone interrupts it. Otherwise the same as
	 * {@link #start(String, String, Duration)}.
	 */
	public TurnStatus start(String sessionId, @Nullable String runId) {
		return start(sessionId, runId, null);
	}

	/**
	 * Starts a turn on the session and returns the new status, {@code RUNNING} under the
	 * run id. The write is version-checked, so when two harnesses start a turn at the
	 * same time exactly one of them succeeds.
	 * <p>
	 * A session on which a turn already runs is busy. The call throws
	 * {@link TurnInProgressException} with the run id of that turn; nothing is written
	 * and nothing is queued. The caller decides what a busy session means for it: fail,
	 * retry later, or {@link #interrupt} the running turn and start again. <pre>{@code
	 * try {
	 *     TurnStatus turn = turns.start(sessionId, runId, Duration.ofMinutes(30));
	 * }
	 * catch (TurnInProgressException ex) {
	 *     // a turn runs under ex.getRunId()
	 * }
	 * }</pre>
	 * <p>
	 * The timeout is the lease, the harness's promise of how long its turn may take. It
	 * is recorded on the {@link EventType#STATUS} event as the instant the lease runs
	 * out, so the policy travels with the turn and the session needs no configuration of
	 * its own. A later start that finds a turn running past its lease does not throw: in
	 * one append it ends that turn with {@link StopReason#EXPIRED} and starts the new
	 * one. This is how a session recovers from a harness that crashed between start and
	 * {@link #end}; without a lease only an {@link #interrupt} frees it.
	 * <p>
	 * A lease that runs out while the turn still runs costs that turn the session: its
	 * {@link #end} becomes a no-op, and its appends still land. Choose the lease for the
	 * slowest turn the harness runs, not the usual one. A turn that runs past it is not
	 * stopped by the session; the harness notices through the status, the interrupt
	 * advisor of the {@code ChatClient} for example, or not at all.
	 * <p>
	 * Starting a turn is optional. A harness that never calls this method leaves the
	 * session idle and appends events freely: an append never requires a running turn.
	 * @param sessionId the session
	 * @param runId the harness's run, the token that owns the turn and ends it with
	 * {@link #end}. The session does not interpret it: one run may hold a turn in each of
	 * several sessions, and successive turns in one. When {@code null} or blank a random
	 * UUID is generated; the returned status carries it.
	 * @param timeout how long the turn may run before another harness may take the
	 * session over; {@code null} for a turn without a lease
	 * @return the new status, {@code RUNNING} under the effective run id, with
	 * {@link TurnStatus#expiresAt()} set when a lease was given
	 * @throws TurnInProgressException if a turn runs on the session and its lease, if
	 * any, has not run out
	 * @throws TurnParkedException if the session is parked on a question; {@link #resume}
	 * answers it and starts the turn
	 * @throws IllegalStateException if the write kept losing to other appends; the
	 * session is left as it was and the call can be retried
	 * @throws IllegalArgumentException if the session does not exist, or the timeout is
	 * zero or negative
	 */
	public TurnStatus start(String sessionId, @Nullable String runId, @Nullable Duration timeout) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		Assert.isTrue(timeout == null || timeout.compareTo(Duration.ZERO) > 0, "timeout must be positive");
		String requestRunId = StringUtils.hasText(runId) ? runId : UUID.randomUUID().toString();
		return writeStatus(sessionId, (version, current) -> {
			Instant now = Instant.now();
			List<SessionEvent> events = new ArrayList<>(2);
			String runningRunId = current.runId();
			if (current.isRunning() && runningRunId != null) {
				if (!current.isExpired(now)) {
					throw new TurnInProgressException(sessionId, runningRunId);
				}
				// The lease ran out: the turn is taken over, its end and the new start
				// land together
				events.add(SessionStatusEvents.idle(sessionId, runningRunId, StopReason.EXPIRED));
			}
			if (current.isParked()) {
				// A new turn would leave the question open: the caller resumes instead
				throw new TurnParkedException(sessionId);
			}
			Instant expiresAt = (timeout != null) ? now.plus(timeout) : null;
			SessionEvent running = SessionStatusEvents.running(sessionId, requestRunId, expiresAt);
			events.add(running);
			return this.sessionService.appendEvents(sessionId, events, version) ? TurnStatus.from(running) : null;
		}, () -> "A turn could not be started on session '" + sessionId + "': the log kept changing");
	}

	/**
	 * Ends the turn of the run id with the given reason and returns the status after the
	 * call.
	 * <p>
	 * Only the run that holds the turn can end it. When the session is not {@code
	 * RUNNING} under this run id, nothing is written and the status as it stands is
	 * returned: another writer ended the turn first, an {@link #interrupt} for example,
	 * and that end stands. A harness therefore calls this method unconditionally when its
	 * turn is over and reads the result to learn whether its own end was recorded:
	 * <pre>{@code
	 * TurnStatus ended = turns.end(sessionId, runId, StopReason.END_TURN);
	 * if (!runId.equals(ended.runId()) || ended.stopReason() != StopReason.END_TURN) {
	 *     // another writer ended the turn first; ended says how it stands now
	 * }
	 * }</pre>
	 * @param sessionId the session
	 * @param runId the run that started the turn
	 * @param stopReason why the turn ends: {@link StopReason#END_TURN} when the model
	 * answered, {@link StopReason#ERROR} when the call failed
	 * @return the status after the call: {@code IDLE} with this reason when this call
	 * ended the turn, otherwise the status another writer left
	 * @throws IllegalStateException if the write kept losing to other appends; the turn
	 * still runs, so the harness retries the call or leaves the turn to an
	 * {@link #interrupt}
	 */
	public TurnStatus end(String sessionId, String runId, StopReason stopReason) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		Assert.hasText(runId, "runId must not be null or empty");
		Assert.notNull(stopReason, "stopReason must not be null");
		return writeStatus(sessionId, (version, current) -> {
			if (!current.isRunning(runId)) {
				return current;
			}
			SessionEvent idle = SessionStatusEvents.idle(sessionId, runId, stopReason);
			return this.sessionService.appendEvents(sessionId, List.of(idle), version) ? TurnStatus.from(idle) : null;
		}, () -> "The turn of session '" + sessionId + "' under run '" + runId
				+ "' could not be ended: the log kept changing");
	}

	/**
	 * Asks the turn that runs on the session to stop, and ends it. Appends an
	 * {@link EventType#INTERRUPT} event that records the request and, when a turn runs, a
	 * {@link EventType#STATUS} event that ends it with {@link StopReason#INTERRUPTED},
	 * both in one append so that the request and the transition land together. When no
	 * turn runs only the request is recorded.
	 * <p>
	 * Callable from anywhere: a UI's stop button, another instance, a scheduler. The
	 * status moves to {@code IDLE} at once, so the next {@link #start} on the session
	 * succeeds. The harness that runs the turn does not stop by itself, though: it learns
	 * of the interrupt when it polls the status inside its loop, as the ChatClient's
	 * interrupt advisor does before every model round, and its own {@link #end} is then a
	 * no-op, because the interrupt stands.
	 * <p>
	 * A parked session has no turn to stop. The request is recorded and the pause stays
	 * as it is: the question remains open, and the session resumes with an answer, not
	 * with a start. A harness that wants the question closed resumes it with an answer
	 * that says the person moved on, as the ChatClient does when new text arrives.
	 * @param sessionId the session
	 * @param reason why the turn is to stop, recorded with the request; may be
	 * {@code null}
	 * @return the status after the call: {@code IDLE} with {@link StopReason#INTERRUPTED}
	 * when a turn was ended, otherwise the status as it stands
	 * @throws IllegalStateException if the write kept losing to other appends; nothing
	 * was written and the call can be retried
	 * @throws IllegalArgumentException if the session does not exist
	 */
	public TurnStatus interrupt(String sessionId, @Nullable String reason) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		return writeStatus(sessionId, (version, current) -> {
			List<SessionEvent> events = new ArrayList<>(2);
			events.add(SessionStatusEvents.interrupt(sessionId, reason));
			SessionEvent idle = null;
			String runId = current.runId();
			if (current.isRunning() && runId != null) {
				idle = SessionStatusEvents.idle(sessionId, runId, StopReason.INTERRUPTED);
				events.add(idle);
			}
			if (!this.sessionService.appendEvents(sessionId, events, version)) {
				return null;
			}
			return (idle != null) ? TurnStatus.from(idle) : current;
		}, () -> "Session '" + sessionId + "' could not be interrupted: the log kept changing");
	}

	/**
	 * Parks the turn on a question and returns the new status, {@code IDLE} with
	 * {@link StopReason#REQUIRES_ACTION}. The turn stops to wait for an answer from
	 * outside: a person's decision on a tool call, for example. Two events land in one
	 * append: a {@link EventType#QUESTION} event with the question, and the status event
	 * that ends the turn. The status says only that the session is parked; the question
	 * says what it is about, the reply with the tool calls for example, in its payload. A
	 * parked session is idle, so no lease runs while the answer takes its time;
	 * {@link #resume} starts the turn again.
	 * <p>
	 * Keyed by the run id, like {@link #end}: when the turn no longer runs under it,
	 * nothing is written and the status as it stands is returned, and
	 * {@link TurnStatus#isParked()} tells whether the question was recorded.
	 * @param sessionId the session
	 * @param runId the run that holds the turn
	 * @param question what the turn waits on
	 * @return the status after the call: parked when this call parked the turn, otherwise
	 * the status another writer left
	 * @throws IllegalStateException if the write kept losing to other appends; the turn
	 * still runs
	 */
	public TurnStatus park(String sessionId, String runId, Question question) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		Assert.hasText(runId, "runId must not be null or empty");
		Assert.notNull(question, "question must not be null");
		return writeStatus(sessionId, (version, current) -> {
			if (!current.isRunning(runId)) {
				return current;
			}
			SessionEvent asked = QuestionEvents.ask(sessionId, question);
			SessionEvent idle = SessionStatusEvents.idle(sessionId, runId, StopReason.REQUIRES_ACTION);
			return this.sessionService.appendEvents(sessionId, List.of(asked, idle), version) ? TurnStatus.from(idle)
					: null;
		}, () -> "The turn of session '" + sessionId + "' under run '" + runId
				+ "' could not be parked: the log kept changing");
	}

	/**
	 * Returns the question the session waits on: the newest {@link EventType#QUESTION}
	 * event that no {@link EventType#ANSWER} event points at, or {@code null} when every
	 * question has its answer. Read it with {@link Question#from}. A parked session
	 * ({@link TurnStatus#isParked()}) has one, and {@link #resume} answers it.
	 */
	public @Nullable SessionEvent pendingQuestion(String sessionId) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		return QuestionEvents.pending(this.sessionService.getEvents(sessionId, QuestionEvents.QUESTIONS_AND_ANSWERS));
	}

	/**
	 * Resumes a parked session with the answer to its question, without a lease; see
	 * {@link #resume(String, String, Object, Duration)}.
	 */
	public TurnStatus resume(String sessionId, @Nullable String runId, @Nullable Object answer) {
		return resume(sessionId, runId, answer, null);
	}

	/**
	 * Resumes a parked session with the answer to its question and returns the new
	 * status, {@code RUNNING} under the run id. Two events land in one append: an
	 * {@link EventType#ANSWER} event that points at the pending question and carries the
	 * answer, and the status event that starts the turn again. The run id and the lease
	 * follow the rules of {@link #start(String, String, Duration)}: the caller's run id
	 * or a fresh one, a lease or none. A run spans the pause when it passes the id it
	 * parked under.
	 * <p>
	 * Only a parked session resumes. A session that runs a turn, because another resume
	 * landed first, is busy: {@link TurnInProgressException}, whether or not that turn's
	 * lease ran out, since the question already has its answer and there is nothing left
	 * to resume; the next {@link #start(String, String, Duration)} takes an expired turn
	 * over. A session that is idle for another reason, or parked without an open
	 * question, cannot resume: {@link IllegalStateException}. Nothing is written in
	 * either case, so the same answer sent twice resumes once, and the tools it allows
	 * run once.
	 * @param sessionId the session
	 * @param runId the run the resumed turn runs under; {@code null} for a fresh one
	 * @param answer the answer, stored as JSON; {@code null} for an acknowledgement
	 * @param timeout the lease; {@code null} for a turn that never expires
	 * @return the status after the call: {@code RUNNING} under the effective run id
	 * @throws TurnInProgressException if a turn runs on the session
	 * @throws IllegalStateException if the session is not parked or has no open question,
	 * or if the write kept losing to other appends
	 * @throws IllegalArgumentException if the timeout is zero or negative
	 */
	public TurnStatus resume(String sessionId, @Nullable String runId, @Nullable Object answer,
			@Nullable Duration timeout) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		Assert.isTrue(timeout == null || timeout.compareTo(Duration.ZERO) > 0, "timeout must be positive");
		String requestRunId = StringUtils.hasText(runId) ? runId : UUID.randomUUID().toString();
		return writeStatus(sessionId, (version, current) -> {
			String runningRunId = current.runId();
			if (current.isRunning() && runningRunId != null) {
				throw new TurnInProgressException(sessionId, runningRunId);
			}
			if (!current.isParked()) {
				throw new IllegalStateException("Session '" + sessionId + "' is not parked on a question; it is "
						+ current.status() + (current.stopReason() != null ? " with " + current.stopReason() : ""));
			}
			SessionEvent pending = pendingQuestion(sessionId);
			if (pending == null) {
				throw new IllegalStateException(
						"Session '" + sessionId + "' is parked but has no open question to answer");
			}
			Instant expiresAt = (timeout != null) ? Instant.now().plus(timeout) : null;
			SessionEvent answered = QuestionEvents.answer(sessionId, pending.getId(), answer);
			SessionEvent running = SessionStatusEvents.running(sessionId, requestRunId, expiresAt);
			return this.sessionService.appendEvents(sessionId, List.of(answered, running), version)
					? TurnStatus.from(running) : null;
		}, () -> "Session '" + sessionId + "' could not be resumed: the log kept changing");
	}

	/**
	 * Runs one status write as the class description says: reads the log's version, then
	 * the status, and hands both to the attempt, which decides and writes against that
	 * version. An attempt that returns {@code null} lost the write to another append and
	 * is repeated after a fresh read. Every attempt costs two reads and one write, so the
	 * retries are spaced by the store's latency without a pause of their own.
	 * @param attempt given the version and the status as read; returns the status after
	 * the attempt, or {@code null} when its write was refused
	 * @param failure the message of the exception thrown when every attempt lost
	 */
	private TurnStatus writeStatus(String sessionId, BiFunction<Long, TurnStatus, @Nullable TurnStatus> attempt,
			Supplier<String> failure) {
		int attempts = 5;
		for (int i = 1;; i++) {
			long version = this.sessionService.getEventVersion(sessionId);
			TurnStatus current = status(sessionId);
			TurnStatus result = attempt.apply(version, current);
			if (result != null) {
				return result;
			}
			if (i == attempts) {
				throw new IllegalStateException(failure.get());
			}
		}
	}

}
