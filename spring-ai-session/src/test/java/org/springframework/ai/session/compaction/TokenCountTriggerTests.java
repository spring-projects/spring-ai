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

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.content.MediaContent;
import org.springframework.ai.session.EventType;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link TokenCountTrigger}.
 */
class TokenCountTriggerTests extends CompactionTestSupport {

	private static final String SESSION_ID = "test-session";

	/**
	 * Deterministic estimator: each character counts as one token. Makes token arithmetic
	 * exact and independent of the real BPE tokenizer.
	 */
	private static final TokenCountEstimator CHAR_ESTIMATOR = new TokenCountEstimator() {
		@Override
		public int estimate(String text) {
			return (text != null) ? text.length() : 0;
		}

		@Override
		public int estimate(MediaContent content) {
			return 0;
		}

		@Override
		public int estimate(Iterable<MediaContent> messages) {
			return 0;
		}
	};

	@Test
	void firesWhenTokenCountReachesThreshold() {
		// "User: hello" (11) + "Assistant: world" (16) = exactly 27 chars.
		// threshold = 27
		TokenCountTrigger trigger = TokenCountTrigger.builder()
			.threshold(27)
			.tokenCountEstimator(CHAR_ESTIMATOR)
			.build();
		CompactionRequest request = requestWith(turn("hello", "world"));

		assertThat(trigger.shouldCompact(request)).isTrue();
	}

	@Test
	void firesWhenTokenCountExceedsThreshold() {
		// "User: hello" (11) + "Assistant: world!" (17) = exactly 28 chars.
		// threshold = 27
		TokenCountTrigger trigger = TokenCountTrigger.builder()
			.threshold(27)
			.tokenCountEstimator(CHAR_ESTIMATOR)
			.build();
		CompactionRequest request = requestWith(turn("hello", "world!"));

		assertThat(trigger.shouldCompact(request)).isTrue();
	}

	@Test
	void doesNotFireWhenTokenCountBelowThreshold() {
		// "User: hi" (8) + "Assistant: ok" (13) = exactly 21 chars.
		// threshold = 22
		TokenCountTrigger trigger = TokenCountTrigger.builder()
			.threshold(22)
			.tokenCountEstimator(CHAR_ESTIMATOR)
			.build();
		CompactionRequest request = requestWith(turn("hi", "ok"));

		assertThat(trigger.shouldCompact(request)).isFalse();
	}

	@Test
	void ignoresEventsThatAreNotMessages() {
		List<SessionEvent> withStatus = new java.util.ArrayList<>(turn("hi", "hello"));
		withStatus.add(SessionEvent.builder()
			.sessionId(SESSION_ID)
			.type(EventType.of("STATUS"))
			.payload("status", "IDLE")
			.build());
		TokenCountTrigger trigger = TokenCountTrigger.builder()
			.threshold(1)
			.tokenCountEstimator(CHAR_ESTIMATOR)
			.build();

		assertThat(trigger.shouldCompact(request(withStatus))).isTrue();
	}

	@Test
	void doesNotFireOnEmptySession() {
		TokenCountTrigger trigger = TokenCountTrigger.builder()
			.threshold(10)
			.tokenCountEstimator(CHAR_ESTIMATOR)
			.build();
		CompactionRequest request = requestWith(List.of());

		assertThat(trigger.shouldCompact(request)).isFalse();
	}

	@Test
	void countsTokensAcrossAllEvents() {
		// Turn 1: "User: ab" (8) + "Assistant: cd" (13) = 21 chars
		// Turn 2: "User: ef" (8) + "Assistant: gh" (13) = 21 chars
		// Total = 42 chars. threshold = 41
		TokenCountTrigger trigger = TokenCountTrigger.builder()
			.threshold(35)
			.tokenCountEstimator(CHAR_ESTIMATOR)
			.build();
		CompactionRequest request = requestWith(turn("ab", "cd"), turn("ef", "gh"));

		assertThat(trigger.shouldCompact(request)).isTrue();
	}

	@Test
	void zeroThresholdIsRejected() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> TokenCountTrigger.builder().threshold(0).tokenCountEstimator(CHAR_ESTIMATOR).build())
			.withMessageContaining("threshold must be greater than 0");
	}

	@Test
	void negativeThresholdIsRejected() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> TokenCountTrigger.builder().threshold(-1).tokenCountEstimator(CHAR_ESTIMATOR).build());
	}

	@Test
	void nullEstimatorIsRejected() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> TokenCountTrigger.builder().threshold(100).tokenCountEstimator(null).build())
			.withMessageContaining("tokenCountEstimator must not be null");
	}

	@Test
	void builderDefaultsToJTokkitEstimator() {
		TokenCountTrigger trigger = TokenCountTrigger.builder().threshold(1).build();

		assertThat(trigger.shouldCompact(requestWith(turn("hello", "world")))).isTrue();
		assertThat(trigger.shouldCompact(requestWith(List.of()))).isFalse();
	}

	@Test
	void supersededSystemMessagesDoNotCountTowardThreshold() {
		// Measures what the strategy budgets: the latest system message, the summaries
		// and
		// the conversation. Superseded system messages are not sent, so they must not
		// make
		// the trigger fire.
		// "System: new" (11) + "User: hi" (8) + "Assistant: ok" (13) = 32.
		TokenCountTrigger trigger = TokenCountTrigger.builder()
			.threshold(33)
			.tokenCountEstimator(CHAR_ESTIMATOR)
			.build();
		List<SessionEvent> events = new ArrayList<>();
		events.add(SessionEvent.builder()
			.sessionId(SESSION_ID)
			.message(new SystemMessage("old xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"))
			.build());
		events.add(SessionEvent.builder().sessionId(SESSION_ID).message(new SystemMessage("new")).build());
		events.addAll(turn("hi", "ok"));

		assertThat(trigger.shouldCompact(requestWith(events))).isFalse();
		assertThat(TokenCountTrigger.builder()
			.threshold(32)
			.tokenCountEstimator(CHAR_ESTIMATOR)
			.build()
			.shouldCompact(requestWith(events))).isTrue();
	}

	@Test
	void getThresholdReturnsConfiguredValue() {
		TokenCountTrigger trigger = TokenCountTrigger.builder()
			.threshold(500)
			.tokenCountEstimator(CHAR_ESTIMATOR)
			.build();
		assertThat(trigger.getThreshold()).isEqualTo(500);
	}

	// --- helpers ---

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
