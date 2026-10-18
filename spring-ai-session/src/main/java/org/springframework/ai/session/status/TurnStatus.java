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

import java.time.Instant;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.session.EventType;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.util.Assert;

/**
 * The turn status of a session: whether a turn runs on it, under which run id, and why
 * the last one ended. It is read from the session's log, not stored on the session: the
 * newest {@link EventType#STATUS} event, typed, or {@link #idle()} for a session that has
 * none. {@link SessionService#status} returns it; {@link SessionService#startTurn},
 * {@link SessionService#endTurn} and {@link SessionService#interrupt} change it.
 *
 * @param status whether a turn runs
 * @param runId the run id of the turn that runs, or of the last one that ended;
 * {@code null} when no turn ran yet
 * @param stopReason why the last turn ended; {@code null} while a turn runs, and when no
 * turn ran yet
 * @param since when the status was recorded, the timestamp of its event; {@code null}
 * when no turn ran yet
 * @param eventId the id of the status event; {@code null} when no turn ran yet
 * @param expiresAt when the running turn's lease runs out, after which
 * {@link SessionService#startTurn(String, String, java.time.Duration) startTurn} may take
 * the session over; {@code null} for a turn without a lease, and while no turn runs
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record TurnStatus(SessionStatus status, @Nullable String runId, @Nullable StopReason stopReason,
		@Nullable Instant since, @Nullable String eventId, @Nullable Instant expiresAt) {

	private static final TurnStatus NO_TURN = new TurnStatus(SessionStatus.IDLE, null, null, null, null, null);

	public TurnStatus {
		Assert.notNull(status, "status must not be null");
		if (status == SessionStatus.RUNNING) {
			Assert.hasText(runId, "a running turn must have a run id");
			Assert.isNull(stopReason, "a running turn has no stop reason");
		}
		else {
			Assert.isNull(expiresAt, "an idle session has no lease");
		}
	}

	/**
	 * Returns the status of a session on which no turn ran yet: idle, without an event.
	 */
	public static TurnStatus idle() {
		return NO_TURN;
	}

	/**
	 * Reads the status a {@link EventType#STATUS} event records.
	 * @throws IllegalArgumentException if the event is not a status event
	 */
	public static TurnStatus from(SessionEvent event) {
		Assert.notNull(event, "event must not be null");
		return new TurnStatus(SessionStatusEvents.status(event), SessionStatusEvents.runId(event),
				SessionStatusEvents.stopReason(event), event.getTimestamp(), event.getId(),
				SessionStatusEvents.expiresAt(event));
	}

	/** Returns whether a turn runs on the session. */
	public boolean isRunning() {
		return this.status == SessionStatus.RUNNING;
	}

	/** Returns whether a turn runs on the session under the given run id. */
	public boolean isRunning(String runId) {
		return isRunning() && runId.equals(this.runId);
	}

	/**
	 * Returns whether a turn runs on the session whose lease has run out at the given
	 * instant, so that a
	 * {@link SessionService#startTurn(String, String, java.time.Duration) startTurn}
	 * would take the session over. A turn without a lease never expires.
	 * @param now the instant to judge the lease by
	 */
	public boolean isExpired(Instant now) {
		Assert.notNull(now, "now must not be null");
		return isRunning() && this.expiresAt != null && !now.isBefore(this.expiresAt);
	}

}
