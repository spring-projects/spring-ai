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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.ai.session.SessionEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link TurnCountTrigger}.
 */
class TurnCountTriggerTests {

	private static final String SESSION_ID = "test-session";

	@Test
	void doesNotFireWhenTurnsUnderThreshold() {
		TurnCountTrigger trigger = new TurnCountTrigger(5);
		// 3 turns (3 user messages)
		CompactionRequest request = requestWith(turn("q1", "a1"), turn("q2", "a2"), turn("q3", "a3"));

		assertThat(trigger.shouldCompact(request)).isFalse();
	}

	@Test
	void doesNotFireWhenTurnsAtExactThreshold() {
		TurnCountTrigger trigger = new TurnCountTrigger(3);
		CompactionRequest request = requestWith(turn("q1", "a1"), turn("q2", "a2"), turn("q3", "a3"));

		assertThat(trigger.shouldCompact(request)).isFalse();
	}

	@Test
	void firesWhenTurnsExceedThreshold() {
		TurnCountTrigger trigger = new TurnCountTrigger(3);
		// 4 turns — exceeds threshold of 3
		CompactionRequest request = requestWith(turn("q1", "a1"), turn("q2", "a2"), turn("q3", "a3"), turn("q4", "a4"));

		assertThat(trigger.shouldCompact(request)).isTrue();
	}

	@Test
	void doesNotFireOnEmptySession() {
		TurnCountTrigger trigger = new TurnCountTrigger(3);
		CompactionRequest request = requestWith();

		assertThat(trigger.shouldCompact(request)).isFalse();
	}

	@Test
	void syntheticEventsAreNotCountedAsTurns() {
		TurnCountTrigger trigger = new TurnCountTrigger(2);

		List<SessionEvent> events = new ArrayList<>();
		// 5 synthetic summary turns (10 events total) — none should count as real turns
		for (int i = 0; i < 5; i++) {
			events.add(SessionEvent.builder()
				.sessionId(SESSION_ID)
				.message(new UserMessage("Summarize the conversation we had so far."))
				.metadata(SessionEvent.METADATA_SYNTHETIC, true)
				.metadata(SessionEvent.METADATA_COMPACTION_SOURCE, "test")
				.build());
			events.add(SessionEvent.builder()
				.sessionId(SESSION_ID)
				.message(new AssistantMessage("summary-" + i))
				.metadata(SessionEvent.METADATA_SYNTHETIC, true)
				.metadata(SessionEvent.METADATA_COMPACTION_SOURCE, "test")
				.build());
		}
		// 2 real turns — exactly at threshold
		events.addAll(turn("q1", "a1"));
		events.addAll(turn("q2", "a2"));

		CompactionRequest request = requestWith(events);

		assertThat(trigger.shouldCompact(request)).isFalse();
	}

	@Test
	void maxTurnsZeroIsRejected() {
		assertThatThrownBy(() -> new TurnCountTrigger(0)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("maxTurns must be greater than 0");
	}

	@Test
	void maxTurnsNegativeIsRejected() {
		assertThatThrownBy(() -> new TurnCountTrigger(-1)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void getMaxTurnsReturnsConfiguredValue() {
		TurnCountTrigger trigger = new TurnCountTrigger(7);
		assertThat(trigger.getMaxTurns()).isEqualTo(7);
	}

	// --- helpers ---

	private List<SessionEvent> turn(String userText, String assistantText) {
		return List.of(SessionEvent.builder().sessionId(SESSION_ID).message(new UserMessage(userText)).build(),
				SessionEvent.builder().sessionId(SESSION_ID).message(new AssistantMessage(assistantText)).build());
	}

	@SafeVarargs
	private CompactionRequest requestWith(List<SessionEvent>... turns) {
		List<SessionEvent> all = new ArrayList<>();
		for (List<SessionEvent> turn : turns) {
			all.addAll(turn);
		}
		return requestWith(all);
	}

	private CompactionRequest requestWith(List<SessionEvent> events) {
		Session session = Session.builder()
			.id(SESSION_ID)
			.metadata(SessionAttributes.PRINCIPAL_NAME, "test-user")
			.build();
		return CompactionRequest.of(session, new ArrayList<>(events));
	}

}
