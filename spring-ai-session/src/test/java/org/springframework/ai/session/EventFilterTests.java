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

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.compaction.CompactionBoundary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The read contract of {@link EventFilter}, as a store applies it to one session's log:
 * the view, the per-event criteria, the window that keeps turns whole, and how two
 * filters merge. {@link EventFilter#apply(List)} is the reference implementation a store
 * must agree with.
 *
 * @author Christian Tzolov
 */
class EventFilterTests {

	private final SessionEvent system = event(new SystemMessage("Be brief."));

	private final SessionEvent user1 = event(new UserMessage("hello"));

	private final SessionEvent assistant1 = event(new AssistantMessage("hi"));

	private final SessionEvent user2 = event(new UserMessage("and now?"));

	private final SessionEvent assistant2 = event(new AssistantMessage("still here"));

	private final SessionEvent boundary = SessionEvent.builder()
		.id("b1")
		.sessionId("s")
		.type(EventType.COMPACTION)
		.payload(new CompactionBoundary("b1", List.of(this.system.getId()), List.of(),
				List.of(this.user2.getId(), this.assistant2.getId()), 2, 0, "test")
			.toPayload())
		.build();

	private final List<SessionEvent> log = List.of(this.system, this.user1, this.assistant1, this.user2,
			this.assistant2);

	// --- the view ---

	@Test
	void allReturnsWholeLogInOrder() {
		List<SessionEvent> withBoundary = List.of(this.system, this.user1, this.assistant1, this.user2, this.assistant2,
				this.boundary);

		assertThat(EventFilter.all().apply(withBoundary)).containsExactlyElementsOf(withBoundary);
	}

	@Test
	void activeProjectsNewestBoundaryAndKeepsMessageEventsOnly() {
		List<SessionEvent> withBoundary = List.of(this.system, this.user1, this.assistant1, this.user2, this.assistant2,
				this.boundary);

		// Pinned first, then what the boundary preserved; the covered turn and the
		// boundary
		// itself are out
		assertThat(EventFilter.active().apply(withBoundary)).containsExactly(this.system, this.user2, this.assistant2);
		assertThat(EventFilter.active().activeView()).isTrue();
		assertThat(EventFilter.active().eventTypes()).containsExactly(EventType.MESSAGE);
	}

	@Test
	void activeViewOfLogWithoutBoundaryIsWholeLog() {
		assertThat(EventFilter.active().apply(this.log)).containsExactlyElementsOf(this.log);
	}

	@Test
	void applyNeverReturnsInputList() {
		assertThat(EventFilter.all().apply(this.log)).isNotSameAs(this.log);
	}

	// --- per-event criteria ---

	@Test
	void messageTypesSelectsMessagesOfGivenTypes() {
		EventFilter filter = EventFilter.builder().messageTypes(Set.of(MessageType.USER)).build();

		assertThat(filter.apply(this.log)).containsExactly(this.user1, this.user2);
		assertThat(filter.matches(this.boundary)).as("a non-message event has no message type").isFalse();
	}

	@Test
	void eventTypesSelectsGivenTypes() {
		List<SessionEvent> withBoundary = List.of(this.user1, this.boundary);

		assertThat(EventFilter.builder().eventTypes(Set.of(EventType.COMPACTION)).build().apply(withBoundary))
			.containsExactly(this.boundary);
	}

	@Test
	void emptyTypeSetsMeanNoRestriction() {
		EventFilter filter = EventFilter.builder().messageTypes(Set.of()).eventTypes(Set.of()).build();

		assertThat(filter.messageTypes()).isNull();
		assertThat(filter.eventTypes()).isNull();
		assertThat(filter.apply(this.log)).containsExactlyElementsOf(this.log);
	}

	@Test
	void excludeSyntheticDropsSyntheticEvents() {
		SessionEvent summary = SessionEvent.builder()
			.sessionId("s")
			.message(new AssistantMessage("Summary so far."))
			.metadata(SessionEvent.METADATA_SYNTHETIC, true)
			.build();
		List<SessionEvent> events = List.of(this.user1, summary, this.assistant1);

		assertThat(EventFilter.builder().excludeSynthetic(true).build().apply(events)).containsExactly(this.user1,
				this.assistant1);
		assertThat(EventFilter.all().apply(events)).containsExactlyElementsOf(events);
	}

	// --- the window keeps turns whole ---

	@Test
	void lastNExtendsBackToStartOfTurnItLandsIn() {
		// The newest event alone would be a reply without its question
		assertThat(EventFilter.lastN(1).apply(this.log)).containsExactly(this.user2, this.assistant2);
		assertThat(EventFilter.lastN(3).apply(this.log)).containsExactly(this.user1, this.assistant1, this.user2,
				this.assistant2);
	}

	@Test
	void lastNStartingOnTurnStartIsNotExtended() {
		assertThat(EventFilter.lastN(2).apply(this.log)).containsExactly(this.user2, this.assistant2);
	}

	@Test
	void lastNCountsNonMessageEventsAndStillExtendsToUserMessage() {
		SessionEvent marker = SessionEvent.builder().sessionId("s").type(EventType.of("STATUS")).build();
		List<SessionEvent> events = List.of(this.user1, this.assistant1, marker, this.user2, marker, this.assistant2);

		assertThat(EventFilter.lastN(1).apply(events)).containsExactly(this.user2, marker, this.assistant2);
	}

	@Test
	void lastNWithoutTurnStartBeforeItIsPlainWindow() {
		// A preamble of system messages has no turn to extend to
		List<SessionEvent> preamble = List.of(this.system, event(new SystemMessage("And polite.")));

		assertThat(EventFilter.lastN(1).apply(preamble)).containsExactly(preamble.get(1));
		// So does a filter that admits no USER message
		EventFilter assistantOnly = EventFilter.builder().messageTypes(Set.of(MessageType.ASSISTANT)).lastN(1).build();
		assertThat(assistantOnly.apply(this.log)).containsExactly(this.assistant2);
	}

	@Test
	void lastNLargerThanLogReturnsWholeLog() {
		assertThat(EventFilter.lastN(100).apply(this.log)).containsExactlyElementsOf(this.log);
	}

	@Test
	void lastNAppliesAfterViewAndCriteria() {
		List<SessionEvent> withBoundary = List.of(this.system, this.user1, this.assistant1, this.user2, this.assistant2,
				this.boundary);
		EventFilter filter = EventFilter.active().toBuilder().lastN(1).build();

		assertThat(filter.apply(withBoundary)).containsExactly(this.user2, this.assistant2);
	}

	@Test
	void lastNMustBePositive() {
		assertThatIllegalArgumentException().isThrownBy(() -> EventFilter.lastN(0));
		assertThatIllegalArgumentException().isThrownBy(() -> EventFilter.Window.last(-1));
	}

	// --- merge: a per-request filter over a configured one ---

	@Test
	void mergeLetsOtherWindowReplaceBaseUnlessAll() {
		assertThat(EventFilter.lastN(10).merge(EventFilter.lastN(3)).window()).isEqualTo(EventFilter.Window.last(3));
		assertThat(EventFilter.lastN(5).merge(EventFilter.all()).window()).isEqualTo(EventFilter.Window.last(5));
		assertThat(EventFilter.all().merge(EventFilter.lastN(2)).window()).isEqualTo(EventFilter.Window.last(2));
	}

	@Test
	void mergeLetsOtherTypesOverrideBase() {
		EventFilter base = EventFilter.builder()
			.messageTypes(Set.of(MessageType.USER))
			.eventTypes(Set.of(EventType.MESSAGE))
			.build();
		EventFilter other = EventFilter.builder()
			.messageTypes(Set.of(MessageType.ASSISTANT))
			.eventTypes(Set.of(EventType.COMPACTION))
			.build();

		assertThat(base.merge(other).messageTypes()).containsExactly(MessageType.ASSISTANT);
		assertThat(base.merge(other).eventTypes()).containsExactly(EventType.COMPACTION);
		assertThat(base.merge(EventFilter.all()).messageTypes()).containsExactly(MessageType.USER);
	}

	@Test
	void mergeOrsFlagsSoEitherSideNarrows() {
		EventFilter synthetic = EventFilter.builder().excludeSynthetic(true).build();

		assertThat(EventFilter.all().merge(synthetic).excludeSynthetic()).isTrue();
		assertThat(synthetic.merge(EventFilter.all()).excludeSynthetic()).isTrue();
		assertThat(EventFilter.all().merge(EventFilter.active()).activeView()).isTrue();
		assertThat(EventFilter.active().merge(EventFilter.all()).activeView()).isTrue();
		assertThat(EventFilter.all().merge(EventFilter.all())).isEqualTo(EventFilter.all());
	}

	private static SessionEvent event(org.springframework.ai.chat.messages.Message message) {
		return SessionEvent.builder().sessionId("s").message(message).build();
	}

}
