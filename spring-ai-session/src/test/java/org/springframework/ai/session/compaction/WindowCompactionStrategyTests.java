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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.MediaContent;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.tokenizer.TokenCountEstimator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link WindowCompactionStrategy}: the three window rules, what every rule
 * pins and covers, and the summarizer.
 *
 * @author Christian Tzolov
 */
class WindowCompactionStrategyTests extends CompactionTestSupport {

	/**
	 * Deterministic estimator: each character of the event's text counts as one token.
	 */
	static final TokenCountEstimator CHAR_ESTIMATOR = new TokenCountEstimator() {

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

	private static final String SHADOW = "Summarize the conversation we had so far.";

	private static final String SUMMARY = "Summary: the user asked about Java and got answers.";

	private static WindowCompactionStrategy lastEvents(int n) {
		return WindowCompactionStrategy.builder().lastEvents(n).build();
	}

	private static WindowCompactionStrategy lastTurns(int n) {
		return WindowCompactionStrategy.builder().lastTurns(n).build();
	}

	private static WindowCompactionStrategy tokenBudget(int maxTokens) {
		return WindowCompactionStrategy.builder().tokenBudget(maxTokens).tokenCountEstimator(CHAR_ESTIMATOR).build();
	}

	/**
	 * The assistant and tool steps of an agent that was never asked anything: no turn.
	 */
	private static List<SessionEvent> turnlessView() {
		List<SessionEvent> view = new ArrayList<>();
		for (int i = 1; i <= 6; i++) {
			view.add(assistant("step " + i));
			view.add(assistant("answer " + i));
		}
		return view;
	}

	@Test
	void nullRequestIsRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> lastEvents(5).compact(null));
	}

	@Test
	void windowSizesMustBePositive() {
		assertThatIllegalArgumentException().isThrownBy(() -> WindowCompactionStrategy.builder().lastEvents(0))
			.withMessageContaining("size must be greater than 0");
		assertThatIllegalArgumentException().isThrownBy(() -> WindowCompactionStrategy.builder().lastTurns(0));
		assertThatIllegalArgumentException().isThrownBy(() -> WindowCompactionStrategy.builder().tokenBudget(0));
		assertThatIllegalArgumentException().isThrownBy(() -> WindowCompactionStrategy.builder().window(null));
	}

	@Test
	void defaultWindowKeepsLastTwentyEvents() {
		assertThat(WindowCompactionStrategy.builder().build().getWindow()).isEqualTo(CompactionWindow.lastEvents(20))
			.isEqualTo(WindowCompactionStrategy.DEFAULT_WINDOW);
		assertThat(lastTurns(3).getWindow()).isEqualTo(new CompactionWindow(CompactionWindow.Rule.LAST_TURNS, 3));
	}

	@Test
	void emptyViewIsUnchanged() {
		assertThat(lastEvents(3).compact(request(List.of())).isUnchanged()).isTrue();
		assertThat(lastTurns(3).compact(request(List.of())).isUnchanged()).isTrue();
		assertThat(tokenBudget(100).compact(request(List.of())).isUnchanged()).isTrue();
	}

	@Test
	void viewWithoutTurnIsUnchangedWhateverRule() {
		// No user message, so no turn start to cut at: nothing may leave the view
		assertThat(lastEvents(2).compact(request(turnlessView())).isUnchanged()).isTrue();
		assertThat(lastTurns(1).compact(request(turnlessView())).isUnchanged()).isTrue();
		assertThat(tokenBudget(10).compact(request(turnlessView())).isUnchanged()).isTrue();
	}

	@Nested
	class LastEvents {

		@Test
		void eventsUpToLimitAreUnchanged() {
			CompactionResult result = lastEvents(5).compact(request(userMessages(3)));

			assertThat(result.isUnchanged()).isTrue();
			assertThat(result.coveredEventCount()).isZero();
			assertThat(lastEvents(3).compact(request(userMessages(3))).isUnchanged()).isTrue();
		}

		@Test
		void eventsOverLimitKeepLastN() {
			CompactionResult result = lastEvents(3).compact(request(userMessages(5)));

			assertThat(covered(result)).containsExactly("msg-1", "msg-2");
			assertThat(kept(result)).containsExactly("msg-3", "msg-4", "msg-5");
			assertThat(result.summary()).isEmpty();
			assertThat(result.strategy()).isEqualTo("window");
		}

		@Test
		void coveredCountIsEventsBeforeCut() {
			CompactionResult result = lastEvents(2).compact(request(userMessages(7)));

			assertThat(result.coveredEventCount()).isEqualTo(5);
			assertThat(result.preservedEvents()).hasSize(2);
		}

		@Test
		void cutSnapsForwardToTurnStart() {
			// [u1, a1, u2, a2, u3, a3], keep 3: the raw cut lands on a2 and snaps to u3
			CompactionResult result = lastEvents(3)
				.compact(request(events(turn("u1", "a1"), turn("u2", "a2"), turn("u3", "a3"))));

			assertThat(kept(result)).containsExactly("u3", "a3");
			assertThat(covered(result)).containsExactly("u1", "a1", "u2", "a2");
		}

		@Test
		void keptWindowStartsAtUserMessage() {
			List<SessionEvent> view = List.of(user("q1"), assistant("r1"), assistant("r1b"), user("q2"),
					assistant("r2"));

			CompactionResult result = lastEvents(2).compact(request(view));

			assertThat(result.preservedEvents().get(0).getMessage()).isInstanceOf(UserMessage.class);
		}

		@Test
		void oversizeNewestTurnIsKeptInsteadOfCoveringEverything() {
			List<SessionEvent> view = List.of(user("u1"), assistant("a1"), user("u2"), assistant("a2"), assistant("a3"),
					assistant("a4"));

			CompactionResult result = lastEvents(2).compact(request(view));

			assertThat(covered(result)).containsExactly("u1", "a1");
			assertThat(kept(result)).containsExactly("u2", "a2", "a3", "a4");
		}

		@Test
		void singleOversizeTurnIsUnchanged() {
			List<SessionEvent> view = List.of(user("u1"), assistant("a1"), assistant("a2"), assistant("a3"));

			assertThat(lastEvents(2).compact(request(view)).isUnchanged()).isTrue();
		}

		@Test
		void priorSummaryIsPinnedFirstAndUsesNoSlot() {
			// 4 conversation events, keep 3: msg-1 is covered, the summary stays ahead
			CompactionResult result = lastEvents(3)
				.compact(request(events(summaryTurn("prior summary"), userMessages(4))));

			assertThat(result.pinnedEvents()).allMatch(SessionEvent::isSynthetic);
			assertThat(kept(result)).containsExactly(SHADOW, "prior summary", "msg-2", "msg-3", "msg-4");
			assertThat(covered(result)).containsExactly("msg-1");
		}

		@Test
		void storedSystemMessageIsPinnedFirstAndUsesNoSlot() {
			List<SessionEvent> view = List.of(system("Answer in French"), user("u1"), assistant("a1"), user("u2"),
					assistant("a2"));

			CompactionResult result = lastEvents(2).compact(request(view));

			assertThat(kept(result)).containsExactly("Answer in French", "u2", "a2");
			assertThat(covered(result)).containsExactly("u1", "a1");
		}

		@Test
		void onlyLatestStoredSystemMessageIsPinnedSoPerTurnSystemMessagesStayBounded() {
			// Latest wins: a system message stored per turn replaces the previous one;
			// the
			// earlier ones are covered wherever they sit, the latest is pinned first
			List<SessionEvent> view = new ArrayList<>();
			view.add(system("Answer in French"));
			for (int i = 1; i <= 5; i++) {
				view.add(user("u" + i));
				view.add(system("time " + i));
				view.add(assistant("a" + i));
			}

			CompactionResult result = lastEvents(3).compact(request(view));

			assertThat(kept(result)).containsExactly("time 5", "u5", "a5");
			assertThat(covered(result)).contains("Answer in French", "time 1", "time 4").doesNotContain("time 5");
		}

		@Test
		void noCutLeavesSupersededSystemMessagesInPlace() {
			List<SessionEvent> view = List.of(system("Answer in French"), user("u1"), assistant("a1"),
					system("Answer in German"));

			assertThat(lastEvents(20).compact(request(view)).isUnchanged()).isTrue();
		}

		@Test
		void systemMessageSurvivesCompactionOfTurnItWasStoredIn() {
			List<SessionEvent> view = List.of(user("u1"), system("Researcher rules"), assistant("a1"), user("u2"),
					assistant("a2"), user("u3"), assistant("a3"));

			CompactionResult result = lastEvents(2).compact(request(view));

			assertThat(kept(result)).containsExactly("Researcher rules", "u3", "a3");
			assertThat(covered(result)).containsExactly("u1", "a1", "u2", "a2");
		}

		@Test
		void preambleBeforeFirstTurnIsPinned() {
			List<SessionEvent> view = events(List.of(assistant("welcome")), turn("u1", "a1"), turn("u2", "a2"));

			CompactionResult result = lastEvents(2).compact(request(view));

			assertThat(kept(result)).containsExactly("welcome", "u2", "a2");
			assertThat(covered(result)).containsExactly("u1", "a1");
		}

		@Test
		void tokensEstimatedSavedIsPositiveWhenCoveringToolEvents() {
			CompactionResult result = lastEvents(2)
				.compact(request(events(toolTurn("What's the weather?"), turn("Thanks", "You're welcome"))));

			assertThat(result.coveredEvents()).isNotEmpty();
			assertThat(result.tokensEstimatedSaved()).isGreaterThan(0);
		}

	}

	@Nested
	class LastTurns {

		@Test
		void turnsUpToLimitAreUnchanged() {
			List<SessionEvent> view = events(turn("q1", "a1"), turn("q2", "a2"), turn("q3", "a3"));

			assertThat(lastTurns(5).compact(request(view)).isUnchanged()).isTrue();
			assertThat(lastTurns(3).compact(request(view)).isUnchanged()).isTrue();
		}

		@Test
		void oldestTurnsAreCoveredOverLimit() {
			CompactionResult result = lastTurns(2)
				.compact(request(events(turn("q1", "a1"), turn("q2", "a2"), turn("q3", "a3"), turn("q4", "a4"))));

			assertThat(kept(result)).containsExactly("q3", "a3", "q4", "a4");
			assertThat(covered(result)).containsExactly("q1", "a1", "q2", "a2");
		}

		@Test
		void turnIsNeverSplit() {
			List<SessionEvent> turn1 = List.of(user("u1"), assistant("step1"), assistant("step2"));

			CompactionResult result = lastTurns(2).compact(request(events(turn1, turn("u2", "a2"), turn("u3", "a3"))));

			assertThat(covered(result)).containsExactly("u1", "step1", "step2");
			assertThat(kept(result)).containsExactly("u2", "a2", "u3", "a3");
		}

		@Test
		void priorSummariesArePinnedFirst() {
			CompactionResult result = lastTurns(1).compact(request(
					events(summaryTurn("summary-1"), summaryTurn("summary-2"), turn("q1", "a1"), turn("q2", "a2"))));

			assertThat(result.pinnedEvents()).hasSize(4).allMatch(SessionEvent::isSynthetic);
			assertThat(kept(result)).containsExactly(SHADOW, "summary-1", SHADOW, "summary-2", "q2", "a2");
			assertThat(covered(result)).containsExactly("q1", "a1");
		}

		@Test
		void preambleBeforeFirstTurnIsPinned() {
			List<SessionEvent> view = events(List.of(assistant("system boot info")), turn("q1", "a1"),
					turn("q2", "a2"));

			CompactionResult result = lastTurns(1).compact(request(view));

			assertThat(kept(result)).containsExactly("system boot info", "q2", "a2");
			assertThat(covered(result)).containsExactly("q1", "a1");
		}

		@Test
		void latestSystemMessageIsPinnedFirstAndEarlierOnesAreCovered() {
			List<SessionEvent> view = new ArrayList<>();
			view.add(system("Answer in French"));
			view.addAll(turn("u1", "a1"));
			view.add(2, system("Answer in German"));
			view.addAll(turn("u2", "a2"));

			CompactionResult result = lastTurns(1).compact(request(view));

			assertThat(covered(result)).containsExactly("Answer in French", "u1", "a1");
			assertThat(kept(result)).containsExactly("Answer in German", "u2", "a2");
		}

		@Test
		void systemMessageSurvivesWhenItsTurnIsCovered() {
			List<SessionEvent> view = new ArrayList<>(turn("u1", "a1"));
			view.add(1, system("Researcher rules"));
			view.addAll(turn("u2", "a2"));

			CompactionResult result = lastTurns(1).compact(request(view));

			assertThat(covered(result)).containsExactly("u1", "a1");
			assertThat(kept(result)).containsExactly("Researcher rules", "u2", "a2");
		}

		@Test
		void tokensEstimatedSavedIsPositiveWhenCoveringTurnWithToolEvents() {
			CompactionResult result = lastTurns(1)
				.compact(request(events(toolTurn("What's the weather?"), turn("Thanks", "You're welcome"))));

			assertThat(result.coveredEvents()).isNotEmpty();
			assertThat(result.tokensEstimatedSaved()).isGreaterThan(0);
		}

	}

	@Nested
	class TokenBudget {

		@Test
		void viewWithinBudgetIsUnchanged() {
			assertThat(tokenBudget(100).compact(request(turn("hi", "hello"))).isUnchanged()).isTrue();
		}

		@Test
		void viewWithinBudgetIsUnchangedEvenWhenItStartsMidTurn() {
			CompactionResult result = tokenBudget(100)
				.compact(request(events(List.of(assistant("welcome")), turn("hi", "hello"))));

			assertThat(result.isUnchanged()).isTrue();
		}

		@Test
		void eventsOverBudgetAreCovered() {
			// "User: u1" (8) + "Assistant: a1" (13) = 21 per turn; budget 25 fits one
			// turn
			CompactionResult result = tokenBudget(25).compact(request(events(turn("u1", "a1"), turn("u2", "a2"))));

			assertThat(kept(result)).containsExactly("u2", "a2");
			assertThat(covered(result)).containsExactly("u1", "a1");
		}

		@Test
		void scanStopsAtFirstOversizeEventSoKeptWindowIsContiguous() {
			// "User: u2" (8) fits in 10; "Assistant: a1_very_large_text_here" (34) stops
			// the scan
			List<SessionEvent> view = List.of(user("u1"), assistant("a1_very_large_text_here"), user("u2"));

			CompactionResult result = tokenBudget(10).compact(request(view));

			assertThat(kept(result)).containsExactly("u2");
			assertThat(covered(result)).containsExactly("u1", "a1_very_large_text_here");
		}

		@Test
		void partialTurnIsNeverKeptAndOversizeNewestTurnIs() {
			// The raw cut lands on a2 and snaps past the end; the newest turn is kept
			// anyway
			List<SessionEvent> view = List.of(user("u1"), assistant("a1"), user("u2_longtxt"), assistant("a2"));

			CompactionResult result = tokenBudget(4).compact(request(view));

			assertThat(covered(result)).containsExactly("u1", "a1");
			assertThat(kept(result)).containsExactly("u2_longtxt", "a2");
		}

		@Test
		void keptWindowStartsAtUserMessage() {
			CompactionResult result = tokenBudget(6)
				.compact(request(events(turn("aaaa", "bbbb"), turn("cccc", "dddd"), turn("eeee", "ffff"))));

			assertThat(result.preservedEvents().get(0).getMessage()).isInstanceOf(UserMessage.class);
		}

		@Test
		void oversizeNewestTurnIsKeptInsteadOfCoveringEverything() {
			String big = "x".repeat(100);
			CompactionResult result = tokenBudget(30)
				.compact(request(events(turn("hi", "ok"), turn("big question", big))));

			assertThat(covered(result)).containsExactly("hi", "ok");
			assertThat(kept(result)).containsExactly("big question", big);
		}

		@Test
		void singleOversizeTurnIsUnchanged() {
			assertThat(tokenBudget(30).compact(request(turn("big question", "x".repeat(100)))).isUnchanged()).isTrue();
		}

		@Test
		void toolEventsCountTowardBudget() {
			CompactionResult result = tokenBudget(40)
				.compact(request(events(toolTurn("What's the weather?"), turn("Thanks", "You're welcome"))));

			assertThat(result.coveredEvents()).isNotEmpty();
			assertThat(result.tokensEstimatedSaved()).isGreaterThan(0);
		}

		@Test
		void priorSummaryIsPinnedFirstEvenWhenItAloneExceedsBudget() {
			CompactionResult result = tokenBudget(1)
				.compact(request(events(summaryTurn("summary"), turn("hello", "world"), turn("yo", "ok"))));

			assertThat(result.pinnedEvents()).hasSize(2).allMatch(SessionEvent::isSynthetic);
			assertThat(kept(result)).containsExactly(SHADOW, "summary", "yo", "ok");
			assertThat(covered(result)).containsExactly("hello", "world");
		}

		@Test
		void storedSystemMessageIsPinnedFirstAndItsTokensComeOffBudget() {
			// "System: sys" = 11 tokens; each turn 21. Both turns (42) fit 45 without the
			// system message; with it only 34 remain, so the older turn is covered
			List<SessionEvent> view = events(List.of(system("sys")), turn("hi", "ok"), turn("yo", "ok"));

			CompactionResult result = tokenBudget(45).compact(request(view));

			assertThat(covered(result)).containsExactly("hi", "ok");
			assertThat(kept(result)).containsExactly("sys", "yo", "ok");
		}

		@Test
		void preambleIsPinnedAndItsTokensComeOffBudget() {
			// "Assistant: welcome" = 18 tokens; each turn 21: 45 - 18 leaves room for one
			// turn
			List<SessionEvent> view = events(List.of(assistant("welcome")), turn("hi", "ok"), turn("yo", "ok"));

			CompactionResult result = tokenBudget(45).compact(request(view));

			assertThat(covered(result)).containsExactly("hi", "ok");
			assertThat(kept(result)).containsExactly("welcome", "yo", "ok");
		}

		@Test
		void pinnedSystemMessageOverBudgetStillKeepsNewestTurn() {
			List<SessionEvent> view = events(
					List.of(system("a system prompt that is longer than the whole token budget")), turn("q1", "r1"),
					turn("q2", "r2"));

			CompactionResult result = tokenBudget(40).compact(request(view));

			assertThat(covered(result)).containsExactly("q1", "r1");
			assertThat(kept(result)).containsExactly("a system prompt that is longer than the whole token budget", "q2",
					"r2");
		}

	}

	@Nested
	class WithSummarizer {

		private final List<SummaryRequest> requests = new ArrayList<>();

		private WindowCompactionStrategy summarized(int lastEvents) {
			return summarized(lastEvents, List.of(new UserMessage(SHADOW), new AssistantMessage(SUMMARY)));
		}

		private WindowCompactionStrategy summarized(int lastEvents, List<Message> summary) {
			return WindowCompactionStrategy.builder()
				.lastEvents(lastEvents)
				.tokenCountEstimator(CHAR_ESTIMATOR)
				.summarizer(request -> {
					this.requests.add(request);
					return summary;
				})
				.build();
		}

		@Test
		void summaryTurnStandsInForCoveredEvents() {
			CompactionResult result = summarized(3).compact(request(userMessages(6)));

			assertThat(result.summary()).extracting(Message::getMessageType)
				.containsExactly(MessageType.USER, MessageType.ASSISTANT);
			assertThat(kept(result)).containsExactly(SHADOW, SUMMARY, "msg-4", "msg-5", "msg-6");
			assertThat(covered(result)).containsExactly("msg-1", "msg-2", "msg-3");
			assertThat(result.strategy()).isEqualTo("window");
			assertThat(this.requests).hasSize(1);
			SummaryRequest request = this.requests.get(0);
			assertThat(request.priorSummary()).isEmpty();
			assertThat(request.covered()).extracting(e -> e.getMessage().getText())
				.containsExactly("msg-1", "msg-2", "msg-3");
			assertThat(request.preserved()).extracting(e -> e.getMessage().getText())
				.containsExactly("msg-4", "msg-5", "msg-6");
		}

		@Test
		void priorSummaryIsHandedOverThenReplacedAndCovered() {
			CompactionResult result = summarized(2)
				.compact(request(events(summaryTurn("Prior summary text"), userMessages(4))));

			assertThat(result.coveredEvents()).hasSize(4);
			assertThat(result.coveredEvents().stream().filter(SessionEvent::isSynthetic)).hasSize(2);
			assertThat(result.pinnedEvents()).isEmpty();
			assertThat(kept(result)).containsExactly(SHADOW, SUMMARY, "msg-3", "msg-4");
			assertThat(this.requests.get(0).priorSummary()).hasSize(2).allMatch(SessionEvent::isSynthetic);
		}

		@Test
		void summarizerIsNotAskedWhenNothingLeavesView() {
			assertThat(summarized(10).compact(request(userMessages(5))).isUnchanged()).isTrue();
			assertThat(summarized(2).compact(request(turnlessView())).isUnchanged()).isTrue();
			assertThat(
					summarized(2).compact(request(List.of(user("u1"), assistant("a1"), assistant("a2")))).isUnchanged())
				.isTrue();

			assertThat(this.requests).isEmpty();
		}

		@Test
		void summarizerThatDeclinesLeavesViewUnchanged() {
			List<SessionEvent> view = List.of(system("v1"), user("u1"), assistant("a1"), user("u2"), system("v2"),
					assistant("a2"), user("u3"), assistant("a3"));

			assertThat(summarized(2, List.of()).compact(request(view)).isUnchanged()).isTrue();
			assertThat(this.requests).hasSize(1);
		}

		@Test
		void cutSnapsForwardToTurnStart() {
			// [u1, a1, u2, a2a, a2b, u3, a3], keep 3: the raw cut lands on a2b and snaps
			// to u3
			List<SessionEvent> view = List.of(user("u1"), assistant("a1"), user("u2"), assistant("a2a"),
					assistant("a2b"), user("u3"), assistant("a3"));

			CompactionResult result = summarized(3).compact(request(view));

			assertThat(kept(result)).containsExactly(SHADOW, SUMMARY, "u3", "a3");
			assertThat(covered(result)).contains("a2b");
		}

		@Test
		void oversizeNewestTurnIsKeptInsteadOfSummarizingEverything() {
			List<SessionEvent> view = List.of(user("u1"), assistant("a1"), user("u2"), assistant("a2"), assistant("a3"),
					assistant("a4"));

			CompactionResult result = summarized(2).compact(request(view));

			assertThat(covered(result)).containsExactly("u1", "a1");
			assertThat(kept(result)).containsExactly(SHADOW, SUMMARY, "u2", "a2", "a3", "a4");
		}

		@Test
		void storedSystemMessageIsPinnedAndNeverHandedToSummarizer() {
			List<SessionEvent> view = List.of(user("u1"), system("Researcher rules"), assistant("a1"), user("u2"),
					assistant("a2"));

			CompactionResult result = summarized(2).compact(request(view));

			assertThat(covered(result)).containsExactly("u1", "a1");
			assertThat(kept(result)).containsExactly("Researcher rules", SHADOW, SUMMARY, "u2", "a2");
			assertThat(this.requests.get(0).covered()).extracting(e -> e.getMessage().getText())
				.containsExactly("u1", "a1");
		}

		@Test
		void latestStoredSystemMessageIsPinnedAndEarlierOneIsCovered() {
			List<SessionEvent> view = List.of(system("Answer in French"), user("u1"), assistant("a1"),
					system("Answer in German"), user("u2"), assistant("a2"));

			CompactionResult result = summarized(2).compact(request(view));

			assertThat(kept(result)).containsExactly("Answer in German", SHADOW, SUMMARY, "u2", "a2");
			assertThat(covered(result)).containsExactly("Answer in French", "u1", "a1");
			assertThat(this.requests.get(0).covered()).extracting(e -> e.getMessage().getText())
				.containsExactly("u1", "a1");
		}

		@Test
		void tokensEstimatedSavedIsNetOfSummaryTurn() {
			List<SessionEvent> view = List.of(user("u1 " + "x".repeat(200)), assistant("a1 " + "y".repeat(200)),
					user("u2"), assistant("a2"));

			CompactionResult result = summarized(2).compact(request(view));

			int coveredTokens = result.coveredEvents().stream().mapToInt(e -> EventText.of(e).length()).sum();
			int summaryTokens = result.summary().stream().mapToInt(m -> EventText.of(m).length()).sum();
			assertThat(summaryTokens).isPositive();
			assertThat(result.tokensEstimatedSaved()).isEqualTo(coveredTokens - summaryTokens);
		}

	}

}
