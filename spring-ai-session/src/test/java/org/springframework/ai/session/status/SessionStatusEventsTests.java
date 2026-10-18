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

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.EventType;
import org.springframework.ai.session.SessionEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link SessionStatusEvents} and {@link TurnStatus}: the events that record a
 * turn status, and the status read from them.
 *
 * @author Christian Tzolov
 */
class SessionStatusEventsTests {

	@Test
	void runningEventRecordsStatusAndRunId() {
		SessionEvent event = SessionStatusEvents.running("s", "run-1");

		assertThat(event.getType()).isEqualTo(EventType.STATUS);
		assertThat(event.hasMessage()).isFalse();
		assertThat(event.getPayload()).containsOnly(entry(SessionStatusEvents.PAYLOAD_STATUS, "RUNNING"),
				entry(SessionStatusEvents.PAYLOAD_RUN_ID, "run-1"));
		assertThat(SessionStatusEvents.status(event)).isEqualTo(SessionStatus.RUNNING);
		assertThat(SessionStatusEvents.runId(event)).isEqualTo("run-1");
		assertThat(SessionStatusEvents.stopReason(event)).isNull();
		assertThat(SessionStatusEvents.expiresAt(event)).isNull();
	}

	@Test
	void runningEventRecordsTheLeaseWhenGiven() {
		Instant expiresAt = Instant.parse("2026-06-01T12:30:00Z");
		SessionEvent event = SessionStatusEvents.running("s", "run-1", expiresAt);

		assertThat(event.getPayload()).containsEntry(SessionStatusEvents.PAYLOAD_EXPIRES_AT, expiresAt.toString());
		assertThat(SessionStatusEvents.expiresAt(event)).isEqualTo(expiresAt);

		TurnStatus status = TurnStatus.from(event);
		assertThat(status.expiresAt()).isEqualTo(expiresAt);
		assertThat(status.isExpired(expiresAt.minusSeconds(1))).isFalse();
		assertThat(status.isExpired(expiresAt)).isTrue();
		assertThat(TurnStatus.from(SessionStatusEvents.running("s", "run-1")).isExpired(Instant.MAX)).isFalse();
		assertThat(TurnStatus.idle().isExpired(Instant.MAX)).isFalse();
	}

	@Test
	void idleEventRecordsStopReason() {
		SessionEvent event = SessionStatusEvents.idle("s", "run-1", StopReason.INTERRUPTED);

		assertThat(event.getType()).isEqualTo(EventType.STATUS);
		assertThat(SessionStatusEvents.status(event)).isEqualTo(SessionStatus.IDLE);
		assertThat(SessionStatusEvents.runId(event)).isEqualTo("run-1");
		assertThat(SessionStatusEvents.stopReason(event)).isEqualTo(StopReason.INTERRUPTED);
	}

	@Test
	void interruptEventRecordsReasonWhenGiven() {
		SessionEvent with = SessionStatusEvents.interrupt("s", "user pressed stop");
		SessionEvent without = SessionStatusEvents.interrupt("s", null);

		assertThat(with.getType()).isEqualTo(EventType.INTERRUPT);
		assertThat(SessionStatusEvents.reason(with)).isEqualTo("user pressed stop");
		assertThat(without.getPayload()).isEmpty();
		assertThat(SessionStatusEvents.reason(without)).isNull();
	}

	@Test
	void readersRejectEventsOfAnotherType() {
		SessionEvent message = SessionEvent.builder().sessionId("s").message(new UserMessage("hi")).build();
		SessionEvent interrupt = SessionStatusEvents.interrupt("s", null);
		SessionEvent status = SessionStatusEvents.running("s", "run-1");

		assertThatIllegalArgumentException().isThrownBy(() -> SessionStatusEvents.status(message))
			.withMessageContaining("not a STATUS event");
		assertThatIllegalArgumentException().isThrownBy(() -> SessionStatusEvents.runId(interrupt));
		assertThatIllegalArgumentException().isThrownBy(() -> SessionStatusEvents.stopReason(interrupt));
		assertThatIllegalArgumentException().isThrownBy(() -> SessionStatusEvents.reason(status))
			.withMessageContaining("not a INTERRUPT event");
		assertThatIllegalArgumentException().isThrownBy(() -> TurnStatus.from(message));
	}

	@Test
	void statusEventWithoutStatusIsRejected() {
		SessionEvent empty = SessionEvent.builder().sessionId("s").type(EventType.STATUS).build();

		assertThatIllegalArgumentException().isThrownBy(() -> SessionStatusEvents.status(empty))
			.withMessageContaining("names no status");
	}

	@Test
	void turnStatusIsReadFromTheEvent() {
		Instant at = Instant.parse("2026-10-16T10:00:00Z");
		SessionEvent running = SessionEvent.builder()
			.id("e1")
			.sessionId("s")
			.timestamp(at)
			.type(EventType.STATUS)
			.payload(SessionStatusEvents.PAYLOAD_STATUS, "RUNNING")
			.payload(SessionStatusEvents.PAYLOAD_RUN_ID, "run-1")
			.build();

		TurnStatus status = TurnStatus.from(running);

		assertThat(status).isEqualTo(new TurnStatus(SessionStatus.RUNNING, "run-1", null, at, "e1", null));
		assertThat(status.isRunning()).isTrue();
		assertThat(status.isRunning("run-1")).isTrue();
		assertThat(status.isRunning("run-2")).isFalse();
		TurnStatus ended = TurnStatus.from(SessionStatusEvents.idle("s", "run-1", StopReason.END_TURN));
		assertThat(ended.status()).isEqualTo(SessionStatus.IDLE);
		assertThat(ended.runId()).isEqualTo("run-1");
		assertThat(ended.stopReason()).isEqualTo(StopReason.END_TURN);
		assertThat(ended.isRunning()).isFalse();
		assertThat(ended.isRunning("run-1")).isFalse();
	}

	@Test
	void idleStatusOfASessionWithoutTurnsHasNoEvent() {
		TurnStatus idle = TurnStatus.idle();

		assertThat(idle.status()).isEqualTo(SessionStatus.IDLE);
		assertThat(idle.runId()).isNull();
		assertThat(idle.stopReason()).isNull();
		assertThat(idle.since()).isNull();
		assertThat(idle.eventId()).isNull();
		assertThat(idle.isRunning()).isFalse();
	}

	@Test
	void runningStatusNeedsARunIdAndNoStopReason() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new TurnStatus(SessionStatus.RUNNING, null, null, null, null, null));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new TurnStatus(SessionStatus.RUNNING, "run-1", StopReason.END_TURN, null, null, null));
		assertThatIllegalArgumentException().isThrownBy(() -> SessionStatusEvents.running("s", ""));
	}

	private static java.util.Map.Entry<String, Object> entry(String key, Object value) {
		return java.util.Map.entry(key, value);
	}

}
