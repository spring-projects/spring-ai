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

package org.springframework.ai.session.tool;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.EventQuery;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.compaction.WindowCompactionStrategy;
import org.springframework.ai.util.JsonHelper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * The {@code conversation_search} tool of {@link SessionRecallTools}: what it finds in
 * its session, what it returns, and that it searches nothing without a session id.
 *
 * @author Christian Tzolov
 */
class SessionRecallToolsTests {

	private final SessionService sessionService = DefaultSessionService.builder()
		.sessionRepository(InMemorySessionRepository.builder().build())
		.build();

	private final SessionRecallTools tools = SessionRecallTools.builder(this.sessionService).pageSize(2).build();

	private final String sessionId = "s1";

	@BeforeEach
	void conversation() {
		this.sessionService.create(CreateSessionRequest.builder().id(this.sessionId).build());
		this.sessionService.appendMessage(this.sessionId, new UserMessage("Where is my invoice?"));
		this.sessionService.appendMessage(this.sessionId, new AssistantMessage("The invoice was sent on Monday."));
		this.sessionService.appendMessage(this.sessionId, new UserMessage("I want a refund for that invoice."));
		this.sessionService.appendMessage(this.sessionId, new AssistantMessage("The refund is on its way."));
	}

	@Test
	void searchReturnsMatchesAsJsonOldestFirst() {
		List<Map<String, String>> results = results(search("Invoice", null, null));

		assertThat(results).extracting(result -> result.get("text"))
			.containsExactly("Where is my invoice?", "The invoice was sent on Monday.");
		assertThat(results).extracting(result -> result.get("type")).containsExactly("user", "assistant");
		assertThat(results).allSatisfy(result -> assertThat(result).containsOnlyKeys("timestamp", "type", "text"));
	}

	@Test
	void pageSelectsLaterMatches() {
		assertThat(results(search("invoice", null, 1))).extracting(result -> result.get("text"))
			.containsExactly("I want a refund for that invoice.");
		// The page after the last match says so, and does not read as "nothing found"
		assertThat(search("invoice", null, 2)).isEqualTo(SessionRecallTools.NO_MORE_RESULTS);
		// A page below zero is the first page
		assertThat(results(search("invoice", null, -3))).hasSize(2);
	}

	@Test
	void keywordsAreSeparatedByCommasAndCombineWithAnyOrAll() {
		assertThat(results(search("monday, way", null, null))).extracting(result -> result.get("text"))
			.containsExactly("The invoice was sent on Monday.", "The refund is on its way.");
		assertThat(results(search("invoice, refund", " ALL ", null))).extracting(result -> result.get("text"))
			.containsExactly("I want a refund for that invoice.");
		assertThat(results(search("monday, way", " Any ", null))).hasSize(2);
		// A word list without commas is one phrase
		assertThat(search("monday way", null, null)).isEqualTo(SessionRecallTools.NO_RESULTS);
		assertThat(results(search("sent on monday", null, null))).hasSize(1);
		// Another match value is the model's mistake, and it is told
		assertThat(search("monday, way", "both", null)).isEqualTo(SessionRecallTools.UNKNOWN_MATCH_RESULT);
	}

	@Test
	void searchFindsMessagesCompactionTookOutOfContext() {
		this.sessionService.compact(this.sessionId, request -> true,
				WindowCompactionStrategy.builder().lastEvents(1).build());
		assertThat(this.sessionService.getActiveMessages(this.sessionId)).extracting(message -> message.getText())
			.doesNotContain("The invoice was sent on Monday.");

		assertThat(results(search("monday", null, null))).extracting(result -> result.get("text"))
			.containsExactly("The invoice was sent on Monday.");
	}

	@Test
	void searchStaysInSessionOfToolContext() {
		this.sessionService.create(CreateSessionRequest.builder().id("s2").build());
		this.sessionService.appendMessage("s2", new UserMessage("Another user's invoice"));

		assertThat(results(search("invoice", null, null))).extracting(result -> result.get("text"))
			.doesNotContain("Another user's invoice");
		assertThat(this.tools.conversationSearch("invoice", null, null, context("unknown")))
			.isEqualTo(SessionRecallTools.NO_RESULTS);
	}

	@Test
	void searchWithoutSessionIdSearchesNothing() {
		assertThat(this.tools.conversationSearch("invoice", null, null, new ToolContext(Map.of())))
			.isEqualTo(SessionRecallTools.MISSING_SESSION_ID_RESULT);
		assertThat(this.tools.conversationSearch("invoice", null, null, context(" ")))
			.isEqualTo(SessionRecallTools.MISSING_SESSION_ID_RESULT);
	}

	@Test
	void sessionIdOfAnotherTypeIsTakenByItsText() {
		UUID id = UUID.randomUUID();
		this.sessionService.create(CreateSessionRequest.builder().id(id.toString()).build());
		this.sessionService.appendMessage(id.toString(), new UserMessage("A delivery question"));

		String found = this.tools.conversationSearch("delivery", null, null,
				new ToolContext(Map.of(SessionRecallTools.SESSION_ID_CONTEXT_KEY, id)));

		assertThat(results(found)).extracting(result -> result.get("text")).containsExactly("A delivery question");
	}

	@Test
	void searchWithoutKeywordSearchesNothing() {
		assertThat(search(" , ", null, null)).isEqualTo(SessionRecallTools.MISSING_KEYWORD_RESULT);
		assertThat(search("", null, null)).isEqualTo(SessionRecallTools.MISSING_KEYWORD_RESULT);
	}

	@Test
	void fullPageNamesNextPage() {
		// Three matches, two on a page
		assertThat(answer(search("invoice", null, null))).containsEntry("nextPage", 1);
		assertThat(answer(search("invoice", null, 1))).doesNotContainKey("nextPage");
	}

	@Test
	void lastPageSearchCanReadNamesNoNextPage() {
		SessionRecallTools hundred = SessionRecallTools.builder(this.sessionService).pageSize(100).build();
		for (int i = 0; i < EventQuery.MAX_MATCHES + 50; i++) {
			this.sessionService.appendMessage(this.sessionId, new UserMessage("delivery " + i));
		}

		// Page 8 is full and page 9 is within the first 1000 matches
		assertThat(answer(hundred.conversationSearch("delivery", null, 8, context(this.sessionId))))
			.containsEntry("nextPage", 9);
		// Page 9 is full too, but page 10 is beyond what a search reads
		assertThat(answer(hundred.conversationSearch("delivery", null, 9, context(this.sessionId))))
			.doesNotContainKey("nextPage");
		assertThat(hundred.conversationSearch("delivery", null, 10, context(this.sessionId))).startsWith("Error: ");
	}

	@Test
	void excerptIsCutFromTextAsGiven() {
		SessionRecallTools shortTexts = SessionRecallTools.builder(this.sessionService).maxTextLength(20).build();
		// Lower-casing U+0130 gives two characters; the excerpt must not shift by it
		this.sessionService.appendMessage(this.sessionId,
				new UserMessage("\u0130".repeat(100) + " DELIVERY " + "\uD83D\uDE00".repeat(100)));

		String text = results(shortTexts.conversationSearch("delivery", null, null, context(this.sessionId))).get(0)
			.get("text");

		assertThat(text).contains(" DELIVERY ");
		// No half of a two-part character at either cut
		assertThat(text.replace("...", "").chars().filter(c -> Character.isSurrogate((char) c)).count() % 2).isZero();
	}

	@Test
	void longTextIsCutToExcerptAroundKeyword() {
		SessionRecallTools shortTexts = SessionRecallTools.builder(this.sessionService).maxTextLength(40).build();
		this.sessionService.appendMessage(this.sessionId,
				new UserMessage("a".repeat(200) + " the DELIVERY date " + "b".repeat(200)));

		String text = results(shortTexts.conversationSearch("delivery", null, null, context(this.sessionId))).get(0)
			.get("text");

		assertThat(text).startsWith("...").endsWith("...").contains("the DELIVERY date").hasSize(40 + 6);
		// A text within the limit comes back whole
		assertThat(results(shortTexts.conversationSearch("thanks, refund is", null, null, context(this.sessionId))))
			.extracting(result -> result.get("text"))
			.containsExactly("The refund is on its way.");
	}

	@Test
	void queryBeyondLimitsTellsModel() {
		String tooMany = "k0,k1,k2,k3,k4,k5,k6,k7,k8,k9,k10,k11,k12,k13,k14,k15,k16,k17,k18,k19,k20";

		assertThat(search(tooMany, null, null)).startsWith("Error: ").contains("at most 20");
		assertThat(search("x".repeat(201), null, null)).startsWith("Error: ").contains("at most 200");
		assertThat(search("invoice", null, 1_000_000)).startsWith("Error: ").contains("beyond the first 1000");
	}

	@Test
	void noMatchSaysSo() {
		assertThat(search("delivery", null, null)).isEqualTo(SessionRecallTools.NO_RESULTS);
	}

	@Test
	void pageSizeIsBounded() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> SessionRecallTools.builder(this.sessionService).pageSize(0));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> SessionRecallTools.builder(this.sessionService).pageSize(101));
	}

	private String search(String query, String match, Integer page) {
		return this.tools.conversationSearch(query, match, page, context(this.sessionId));
	}

	private static ToolContext context(String sessionId) {
		return new ToolContext(Map.of(SessionRecallTools.SESSION_ID_CONTEXT_KEY, sessionId));
	}

	private static List<Map<String, String>> results(String json) {
		return new JsonHelper().convertFromMap(Map.of("results", answer(json).get("results")), Results.class).results();
	}

	private static Map<String, Object> answer(String json) {
		return new JsonHelper().fromJsonToMap(json);
	}

	private record Results(List<Map<String, String>> results) {
	}

}
