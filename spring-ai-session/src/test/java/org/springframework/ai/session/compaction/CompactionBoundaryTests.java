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

package org.springframework.ai.session.compaction;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.EventType;
import org.springframework.ai.session.SessionEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CompactionBoundary}: the payload round trip and the projection.
 */
class CompactionBoundaryTests extends CompactionTestSupport {

	private static SessionEvent boundary(List<SessionEvent> pinned, List<SessionEvent> summary,
			List<SessionEvent> preserved) {
		String id = UUID.randomUUID().toString();
		CompactionBoundary boundary = new CompactionBoundary(id, ids(pinned), ids(summary), ids(preserved), 3, 42,
				"test");
		return SessionEvent.builder()
			.id(id)
			.sessionId(SESSION_ID)
			.type(EventType.COMPACTION)
			.payload(boundary.toPayload())
			.build();
	}

	private static List<String> ids(List<SessionEvent> events) {
		return events.stream().map(SessionEvent::getId).toList();
	}

	private static List<String> texts(List<SessionEvent> events) {
		return events.stream().map(e -> e.hasMessage() ? e.getMessage().getText() : "<" + e.getType() + ">").toList();
	}

	@Test
	void payloadRoundTripsThroughEvent() {
		CompactionBoundary boundary = new CompactionBoundary("b1", List.of("p1"), List.of("s1", "s2"), List.of("k1"), 7,
				1234, "sliding-window");
		SessionEvent event = SessionEvent.builder()
			.id("b1")
			.sessionId(SESSION_ID)
			.type(EventType.COMPACTION)
			.payload(boundary.toPayload())
			.build();

		assertThat(CompactionBoundary.from(event)).isEqualTo(boundary);
	}

	@Test
	void fromReadsNumbersAsJsonMapperWouldReturnThem() {
		// Counts come back from JSON as Integer or Long; lists as List<Object>
		SessionEvent event = SessionEvent.builder()
			.id("b1")
			.sessionId(SESSION_ID)
			.type(EventType.COMPACTION)
			.payload(Map.of(CompactionBoundary.PAYLOAD_PRESERVED, List.of("k1"),
					CompactionBoundary.PAYLOAD_COVERED_COUNT, 3L, CompactionBoundary.PAYLOAD_TOKENS_SAVED, 10))
			.build();

		CompactionBoundary boundary = CompactionBoundary.from(event);

		assertThat(boundary.preservedEventIds()).containsExactly("k1");
		assertThat(boundary.pinnedEventIds()).isEmpty();
		assertThat(boundary.coveredEventCount()).isEqualTo(3);
		assertThat(boundary.tokensEstimatedSaved()).isEqualTo(10);
		assertThat(boundary.strategy()).isEmpty();
	}

	@Test
	void fromRejectsNonCompactionEvent() {
		assertThatThrownBy(() -> CompactionBoundary.from(user("hi"))).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("COMPACTION");
	}

	@Test
	void logWithoutBoundaryIsItsOwnView() {
		List<SessionEvent> log = List.of(user("u1"), assistant("a1"));

		assertThat(CompactionBoundary.newest(log)).isNull();
		assertThat(CompactionBoundary.project(log)).containsExactlyElementsOf(log).isNotSameAs(log);
	}

	@Test
	void viewIsPinnedThenSummaryThenPreservedThenWhatFollows() {
		SessionEvent sys = system("rules");
		SessionEvent u1 = user("u1");
		SessionEvent a1 = assistant("a1");
		SessionEvent u2 = user("u2");
		SessionEvent a2 = assistant("a2");
		SessionEvent shadow = synthetic(new UserMessage("Summarize."));
		SessionEvent summary = synthetic(new AssistantMessage("summary 1"));
		SessionEvent b = boundary(List.of(sys), List.of(shadow, summary), List.of(u2, a2));
		SessionEvent u3 = user("u3");
		List<SessionEvent> log = List.of(u1, sys, a1, u2, a2, b, shadow, summary, u3);

		List<SessionEvent> view = CompactionBoundary.project(log);

		assertThat(texts(view)).containsExactly("rules", "Summarize.", "summary 1", "u2", "a2", "u3");
		assertThat(view).doesNotContain(b, u1, a1);
		// Projecting a view again changes nothing
		assertThat(CompactionBoundary.project(view)).containsExactlyElementsOf(view);
	}

	@Test
	void newestBoundaryWins() {
		SessionEvent u1 = user("u1");
		SessionEvent u2 = user("u2");
		SessionEvent s1 = synthetic(new AssistantMessage("summary 1"));
		SessionEvent b1 = boundary(List.of(), List.of(s1), List.of(u2));
		SessionEvent u3 = user("u3");
		SessionEvent u4 = user("u4");
		SessionEvent s2 = synthetic(new AssistantMessage("summary 2"));
		// The second compaction covers summary 1, u2 and u3 and keeps u4 verbatim
		SessionEvent b2 = boundary(List.of(), List.of(s2), List.of(u4));
		SessionEvent u5 = user("u5");
		List<SessionEvent> log = List.of(u1, u2, b1, s1, u3, u4, b2, s2, u5);

		assertThat(texts(CompactionBoundary.project(log))).containsExactly("summary 2", "u4", "u5");
		assertThat(CompactionBoundary.newest(log).id()).isEqualTo(b2.getId());
		// Everything stays in the log
		assertThat(log).hasSize(9);
	}

	@Test
	void pinnedEventOlderThanEarlierBoundaryIsStillFound() {
		// The latest stored system message was written before the first compaction and
		// pinned by both boundaries: an id lookup over the whole log finds it.
		SessionEvent sys = system("rules");
		SessionEvent u1 = user("u1");
		SessionEvent b1 = boundary(List.of(sys), List.of(), List.of(u1));
		SessionEvent u2 = user("u2");
		SessionEvent b2 = boundary(List.of(sys), List.of(), List.of(u2));
		List<SessionEvent> log = List.of(sys, u1, b1, u2, b2);

		assertThat(texts(CompactionBoundary.project(log))).containsExactly("rules", "u2");
	}

	@Test
	void idsLogDoesNotHoldAreSkipped() {
		// A boundary may name an event that was never written (or was removed by hand):
		// the view skips it rather than failing the read
		SessionEvent u1 = user("u1");
		SessionEvent u2 = user("u2");
		SessionEvent ghost = user("ghost");
		SessionEvent b = boundary(List.of(ghost), List.of(ghost), List.of(u2, ghost));
		SessionEvent u3 = user("u3");

		assertThat(texts(CompactionBoundary.project(List.of(u1, u2, b, u3)))).containsExactly("u2", "u3");
	}

	@Test
	void eventsAppendedAfterSummaryAreInViewWhateverTheirType() {
		SessionEvent u1 = user("u1");
		SessionEvent s1 = synthetic(new AssistantMessage("summary"));
		SessionEvent b1 = boundary(List.of(), List.of(s1), List.of());
		SessionEvent status = SessionEvent.builder().sessionId(SESSION_ID).type(EventType.of("STATUS")).build();
		SessionEvent u2 = user("u2");

		assertThat(texts(CompactionBoundary.project(List.of(u1, b1, s1, status, u2)))).containsExactly("summary",
				"<STATUS>", "u2");
	}

	@Test
	void idNamedTwiceIsPlacedOnce() {
		SessionEvent sys = system("sys");
		SessionEvent u1 = user("u1");
		SessionEvent u2 = user("u2");

		List<SessionEvent> view = CompactionBoundary
			.project(List.of(sys, u1, u2, boundary(List.of(sys), List.of(), List.of(sys, u2))));

		assertThat(texts(view)).containsExactly("sys", "u2");
	}

}
