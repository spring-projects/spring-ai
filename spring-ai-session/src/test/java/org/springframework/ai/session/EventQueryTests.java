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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.EventQuery.MatchMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The search contract of {@link EventQuery}, as a store applies it to one session's log:
 * which events a query finds, how keywords combine, and how the matches are paged.
 * {@link EventQuery#apply(List)} is the reference implementation a store must agree with.
 *
 * @author Christian Tzolov
 */
class EventQueryTests {

	private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

	private final SessionEvent invoice = event(new UserMessage("Where is my Invoice?"), 0);

	private final SessionEvent sent = event(new AssistantMessage("The invoice was sent on Monday."), 1);

	private final SessionEvent refund = event(new UserMessage("I want a refund for that invoice."), 2);

	private final SessionEvent done = event(new AssistantMessage("The refund is on its way."), 3);

	private final List<SessionEvent> log = List.of(this.invoice, this.sent, this.refund, this.done);

	// --- what a query finds ---

	@Test
	void keywordMatchesTextIgnoringCase() {
		assertThat(EventQuery.keyword("INVOICE").apply(this.log)).containsExactly(this.invoice, this.sent, this.refund);
	}

	@Test
	void anyMatchesTextWithOneOfKeywords() {
		EventQuery query = EventQuery.builder().keywords("monday", "way").build();

		assertThat(query.apply(this.log)).containsExactly(this.sent, this.done);
	}

	@Test
	void allMatchesTextWithEveryKeyword() {
		EventQuery query = EventQuery.builder().keywords("invoice", "refund").matchMode(MatchMode.ALL).build();

		assertThat(query.apply(this.log)).containsExactly(this.refund);
	}

	@Test
	void keywordIsLiteralText() {
		SessionEvent percent = event(new UserMessage("A 100% match_ing [text]!"), 4);
		List<SessionEvent> withPercent = List.of(this.invoice, percent);

		assertThat(EventQuery.keyword("100%").apply(withPercent)).containsExactly(percent);
		assertThat(EventQuery.keyword("match_").apply(withPercent)).containsExactly(percent);
		assertThat(EventQuery.keyword("[text]!").apply(withPercent)).containsExactly(percent);
		assertThat(EventQuery.keyword("%").apply(List.of(this.invoice))).isEmpty();
		assertThat(EventQuery.keyword("_").apply(List.of(this.invoice))).isEmpty();
	}

	@Test
	void caseIsIgnoredLetterByLetter() {
		// A lower-cased copy of the text would end in a final sigma and miss the keyword
		SessionEvent greek = event(new UserMessage("\u039F\u0394\u039F\u03A3"), 4);

		assertThat(EventQuery.keyword("\u03C3").apply(List.of(greek))).containsExactly(greek);
		assertThat(EventQuery.indexOfIgnoreCase("An INVOICE here", "invoice")).isEqualTo(3);
		assertThat(EventQuery.indexOfIgnoreCase("An invoice here", "refund")).isEqualTo(-1);
		assertThat(EventQuery.indexOfIgnoreCase("short", "longer than the text")).isEqualTo(-1);
	}

	@Test
	void messageTypesNarrowSearch() {
		EventQuery query = EventQuery.builder().keywords("invoice").messageTypes(Set.of(MessageType.USER)).build();

		assertThat(query.apply(this.log)).containsExactly(this.invoice, this.refund);
	}

	@Test
	void sinceLeavesOlderEventsOut() {
		EventQuery query = EventQuery.builder().keywords("invoice").since(this.sent.getTimestamp()).build();

		assertThat(query.apply(this.log)).containsExactly(this.sent, this.refund);
	}

	@Test
	void syntheticEventsAreNotSearched() {
		SessionEvent summary = SessionEvent.builder()
			.sessionId("s")
			.message(new AssistantMessage("Summary: the invoice was discussed."))
			.metadata(SessionEvent.METADATA_SYNTHETIC, true)
			.build();

		assertThat(EventQuery.keyword("invoice").apply(List.of(summary, this.invoice))).containsExactly(this.invoice);
	}

	@Test
	void eventsThatAreNotMessagesAreNotSearched() {
		SessionEvent note = SessionEvent.builder()
			.sessionId("s")
			.type(EventType.of("NOTE"))
			.payload("text", "invoice")
			.build();

		assertThat(EventQuery.keyword("invoice").apply(List.of(note, this.invoice))).containsExactly(this.invoice);
	}

	@Test
	void toolResultDataIsNotSearched() {
		SessionEvent result = event(ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("c1", "find_invoice", "invoice 42")))
			.build(), 4);

		assertThat(EventQuery.keyword("invoice").apply(List.of(result))).isEmpty();
	}

	// --- pages ---

	@Test
	void matchesComeOldestFirstOnePageAtTime() {
		List<SessionEvent> many = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			many.add(event(new UserMessage("invoice " + i), i));
		}

		assertThat(texts(EventQuery.builder().keywords("invoice").pageSize(2).build().apply(many)))
			.containsExactly("invoice 0", "invoice 1");
		assertThat(texts(EventQuery.builder().keywords("invoice").pageSize(2).page(1).build().apply(many)))
			.containsExactly("invoice 2", "invoice 3");
		assertThat(texts(EventQuery.builder().keywords("invoice").pageSize(2).page(2).build().apply(many)))
			.containsExactly("invoice 4");
		assertThat(EventQuery.builder().keywords("invoice").pageSize(2).page(3).build().apply(many)).isEmpty();
	}

	@Test
	void limitCoversPageAndEveryPageBeforeIt() {
		assertThat(EventQuery.keyword("a").limit()).isEqualTo(EventQuery.DEFAULT_PAGE_SIZE);
		assertThat(EventQuery.builder().keywords("a").pageSize(7).page(2).build().limit()).isEqualTo(21);
		assertThat(EventQuery.builder().keywords("a").pageSize(100).page(9).build().limit())
			.isEqualTo(EventQuery.MAX_MATCHES);
	}

	// --- the query itself ---

	@Test
	void keywordsAreStrippedAndBlankOnesDropped() {
		EventQuery query = EventQuery.builder().keywords(" invoice ", "  ", "refund", "invoice").build();

		assertThat(query.keywords()).containsExactly("invoice", "refund");
		assertThat(query.matchMode()).isEqualTo(MatchMode.ANY);
		assertThat(query.page()).isZero();
		assertThat(query.pageSize()).isEqualTo(EventQuery.DEFAULT_PAGE_SIZE);
	}

	@Test
	void queryNeedsKeywordAndValidPage() {
		assertThatIllegalArgumentException().isThrownBy(() -> EventQuery.builder().build());
		assertThatIllegalArgumentException().isThrownBy(() -> EventQuery.keyword("  "));
		assertThatIllegalArgumentException().isThrownBy(() -> EventQuery.builder().keywords("a").page(-1).build());
		assertThatIllegalArgumentException().isThrownBy(() -> EventQuery.builder().keywords("a").pageSize(0).build());
		assertThatIllegalArgumentException()
			.isThrownBy(() -> EventQuery.builder().keywords("a").pageSize(EventQuery.MAX_PAGE_SIZE + 1).build());
	}

	@Test
	void queryIsBounded() {
		List<String> tooMany = new ArrayList<>();
		for (int i = 0; i <= EventQuery.MAX_KEYWORDS; i++) {
			tooMany.add("keyword" + i);
		}
		assertThatIllegalArgumentException().isThrownBy(() -> EventQuery.builder().keywords(tooMany).build())
			.withMessageContaining("at most " + EventQuery.MAX_KEYWORDS);
		assertThatIllegalArgumentException()
			.isThrownBy(() -> EventQuery.keyword("a".repeat(EventQuery.MAX_KEYWORD_LENGTH + 1)))
			.withMessageContaining("at most " + EventQuery.MAX_KEYWORD_LENGTH);
		// Pages reach the first MAX_MATCHES matches and no further
		assertThat(EventQuery.builder().keywords("a").pageSize(10).page(99).build().limit())
			.isEqualTo(EventQuery.MAX_MATCHES);
		assertThatIllegalArgumentException()
			.isThrownBy(() -> EventQuery.builder().keywords("a").pageSize(10).page(100).build())
			.withMessageContaining("beyond the first " + EventQuery.MAX_MATCHES);
		assertThatIllegalArgumentException()
			.isThrownBy(() -> EventQuery.builder().keywords("a").page(Integer.MAX_VALUE).build());
		// A store's query may not take this character
		assertThatIllegalArgumentException().isThrownBy(() -> EventQuery.keyword("in\u0000voice"))
			.withMessageContaining("NUL");
	}

	@Test
	void nullKeywordIsDroppedLikeBlankOne() {
		assertThat(EventQuery.builder().keywords("invoice", null, " ").build().keywords()).containsExactly("invoice");
		assertThatIllegalArgumentException().isThrownBy(() -> EventQuery.builder().keywords((String) null).build());
	}

	@Test
	void sinceIsKeptToMicrosecond() {
		Instant nanos = Instant.parse("2026-01-01T00:00:00.123456789Z");
		SessionEvent event = SessionEvent.builder()
			.sessionId("s")
			.timestamp(nanos)
			.message(new UserMessage("invoice"))
			.build();

		EventQuery query = EventQuery.builder().keywords("invoice").since(nanos).build();

		assertThat(query.since()).isEqualTo(Instant.parse("2026-01-01T00:00:00.123456Z"));
		assertThat(query.apply(List.of(event))).containsExactly(event);
	}

	private static SessionEvent event(Message message, int minute) {
		return SessionEvent.builder()
			.sessionId("s")
			.timestamp(START.plusSeconds(60L * minute))
			.message(message)
			.build();
	}

	private static List<String> texts(List<SessionEvent> events) {
		return events.stream().map(event -> event.getMessage().getText()).toList();
	}

}
