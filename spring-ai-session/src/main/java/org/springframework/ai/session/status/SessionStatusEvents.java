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
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.EventType;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.util.Assert;

/**
 * Builds and reads the events that record a session's turn status. A
 * {@link EventType#STATUS} event records each transition, a turn that starts or ends, and
 * an {@link EventType#INTERRUPT} event records each request to stop one. Neither carries
 * a message; the status, the run id and the reason go in the payload under the
 * {@code PAYLOAD_*} keys.
 * <p>
 * {@link SessionService#status} reads the newest status event into a {@link TurnStatus}.
 * The events themselves stay in the log as the audit trail of the session's turns. They
 * are not part of the active view, which holds message events only, so they never reach a
 * prompt and need no compaction rule.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class SessionStatusEvents {

	/** Payload key of a status event: the {@link SessionStatus}, by name. */
	public static final String PAYLOAD_STATUS = "status";

	/** Payload key of a status event: the run id of the turn that starts or ends. */
	public static final String PAYLOAD_RUN_ID = "runId";

	/**
	 * Payload key of a status event that ends a turn: the {@link StopReason}, by name.
	 */
	public static final String PAYLOAD_STOP_REASON = "stopReason";

	/**
	 * Payload key of a status event that starts a turn: when its lease runs out, as an
	 * ISO-8601 instant, when the harness set one.
	 */
	public static final String PAYLOAD_EXPIRES_AT = "expiresAt";

	/**
	 * Payload key of an interrupt event: why the turn was to stop, when a reason was
	 * given.
	 */
	public static final String PAYLOAD_REASON = "reason";

	/**
	 * Selects the newest status event of a session, the one {@link SessionService#status}
	 * reads: one indexed read, by session, event type and position.
	 */
	public static final EventFilter NEWEST_STATUS = EventFilter.builder()
		.eventTypes(Set.of(EventType.STATUS))
		.lastN(1)
		.build();

	private SessionStatusEvents() {
	}

	/**
	 * Creates the status event that starts a turn on the session under the run id,
	 * without a lease: the turn runs until it is ended or interrupted.
	 */
	public static SessionEvent running(String sessionId, String runId) {
		return running(sessionId, runId, null);
	}

	/**
	 * Creates the status event that starts a turn on the session under the run id.
	 * @param expiresAt when the turn's lease runs out, after which another
	 * {@link SessionService#startTurn startTurn} may take the session over; {@code null}
	 * for a turn that never expires
	 */
	public static SessionEvent running(String sessionId, String runId, @Nullable Instant expiresAt) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		Assert.hasText(runId, "runId must not be null or empty");
		SessionEvent.Builder builder = SessionEvent.builder()
			.sessionId(sessionId)
			.type(EventType.STATUS)
			.payload(PAYLOAD_STATUS, SessionStatus.RUNNING.name())
			.payload(PAYLOAD_RUN_ID, runId);
		if (expiresAt != null) {
			builder.payload(PAYLOAD_EXPIRES_AT, expiresAt.toString());
		}
		return builder.build();
	}

	/** Creates the status event that ends the turn of the run id, with the reason. */
	public static SessionEvent idle(String sessionId, String runId, StopReason stopReason) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		Assert.hasText(runId, "runId must not be null or empty");
		Assert.notNull(stopReason, "stopReason must not be null");
		return SessionEvent.builder()
			.sessionId(sessionId)
			.type(EventType.STATUS)
			.payload(PAYLOAD_STATUS, SessionStatus.IDLE.name())
			.payload(PAYLOAD_RUN_ID, runId)
			.payload(PAYLOAD_STOP_REASON, stopReason.name())
			.build();
	}

	/**
	 * Creates the event that records a request to stop the turn that runs on the session.
	 * @param reason why the turn is to stop; {@code null} when none is given
	 */
	public static SessionEvent interrupt(String sessionId, @Nullable String reason) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		SessionEvent.Builder builder = SessionEvent.builder().sessionId(sessionId).type(EventType.INTERRUPT);
		if (reason != null) {
			builder.payload(PAYLOAD_REASON, reason);
		}
		return builder.build();
	}

	/**
	 * Reads the status a status event records.
	 * @throws IllegalArgumentException if the event is not a status event, or names no
	 * status
	 */
	public static SessionStatus status(SessionEvent event) {
		requireType(event, EventType.STATUS);
		Object value = event.getPayload().get(PAYLOAD_STATUS);
		Assert.notNull(value, () -> "Status event '" + event.getId() + "' names no status");
		return SessionStatus.valueOf(String.valueOf(value));
	}

	/**
	 * Reads the run id a status event names.
	 * @throws IllegalArgumentException if the event is not a status event
	 */
	public static @Nullable String runId(SessionEvent event) {
		requireType(event, EventType.STATUS);
		return text(event.getPayload().get(PAYLOAD_RUN_ID));
	}

	/**
	 * Reads the stop reason a status event records; {@code null} for an event that starts
	 * a turn.
	 * @throws IllegalArgumentException if the event is not a status event
	 */
	public static @Nullable StopReason stopReason(SessionEvent event) {
		requireType(event, EventType.STATUS);
		String value = text(event.getPayload().get(PAYLOAD_STOP_REASON));
		return (value != null) ? StopReason.valueOf(value) : null;
	}

	/**
	 * Reads when the lease of the turn a status event starts runs out; {@code null} for a
	 * turn without a lease, and for an event that ends a turn.
	 * @throws IllegalArgumentException if the event is not a status event
	 */
	public static @Nullable Instant expiresAt(SessionEvent event) {
		requireType(event, EventType.STATUS);
		String value = text(event.getPayload().get(PAYLOAD_EXPIRES_AT));
		return (value != null) ? Instant.parse(value) : null;
	}

	/**
	 * Reads the reason an interrupt event gives; {@code null} when none was given.
	 * @throws IllegalArgumentException if the event is not an interrupt event
	 */
	public static @Nullable String reason(SessionEvent event) {
		requireType(event, EventType.INTERRUPT);
		return text(event.getPayload().get(PAYLOAD_REASON));
	}

	private static void requireType(SessionEvent event, EventType type) {
		Assert.notNull(event, "event must not be null");
		Assert.isTrue(type.equals(event.getType()),
				() -> "Event '" + event.getId() + "' is a " + event.getType() + " event, not a " + type + " event");
	}

	private static @Nullable String text(@Nullable Object value) {
		return (value != null) ? String.valueOf(value) : null;
	}

}
