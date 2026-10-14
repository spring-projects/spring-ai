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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.util.Assert;

/**
 * Keeps the recent conversation in the view and covers the rest: the one compaction
 * strategy, configured by a {@link CompactionWindow} (the last events, the last turns or
 * a token budget) and, optionally, a {@link CompactionSummarizer} that condenses what
 * leaves the view into a summary turn.
 *
 * <h2>One pass</h2>
 * <ol>
 * <li>The latest stored system message (the system prompt: "latest wins") is pinned at
 * the front of the view, whatever the cut, and never summarized. Every earlier stored
 * system message is superseded and leaves the view with a pass that cuts, wherever it
 * sits.</li>
 * <li>The events before the first turn (a pre-seeded preamble) are pinned too: they are
 * not a turn and are never cut.</li>
 * <li>Without a summarizer the summary a previous pass left is pinned and carried
 * forward; with one it is handed to the summarizer as the prior summary and covered by
 * the new one.</li>
 * <li>The window rule gives the raw cut among the turns; the cut is moved forward to the
 * next turn start, so a turn is never split, and back to the newest turn when it would
 * cover everything, so the newest turn stays even when it alone exceeds the window.</li>
 * <li>The turns before the cut leave the view (covered), the rest stay (preserved). With
 * a summarizer, the summary turn stands in for the covered events; a summarizer that
 * returns nothing leaves the view unchanged.</li>
 * </ol>
 * Pinned events use no slot of an event or turn window and come off a token budget first.
 * The log is never changed: {@link org.springframework.ai.session.SessionService#compact}
 * records the decision as an appended boundary, and the view from then on is the pinned
 * events, the summary and the preserved events.
 *
 * <h2>No-op</h2>
 * <p>
 * Nothing is written when the turns fit the window, when the view has no turn (no user
 * message, so no turn start to cut at), or when the summarizer declines.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class WindowCompactionStrategy implements CompactionStrategy {

	/** The window of a strategy built without one: the last 20 events. */
	public static final CompactionWindow DEFAULT_WINDOW = CompactionWindow.lastEvents(20);

	private static final String STRATEGY_NAME = "window";

	private final CompactionWindow window;

	private final TokenCountEstimator tokenCountEstimator;

	@Nullable private final CompactionSummarizer summarizer;

	private WindowCompactionStrategy(Builder builder) {
		this.window = builder.window;
		this.tokenCountEstimator = builder.tokenCountEstimator;
		this.summarizer = builder.summarizer;
	}

	public static Builder builder() {
		return new Builder();
	}

	/** The rule this strategy cuts by. */
	public CompactionWindow getWindow() {
		return this.window;
	}

	@Override
	public CompactionResult compact(CompactionRequest request) {
		Assert.notNull(request, "request must not be null");
		List<SessionEvent> view = request.events();
		ToIntFunction<SessionEvent> tokens = e -> this.tokenCountEstimator.estimate(EventText.of(e));

		// 1-3. What is pinned, what a previous pass summarized, and the conversation
		List<SessionEvent> pinnedSystem = latestStoredSystem(view);
		List<SessionEvent> priorSummary = view.stream().filter(SessionEvent::isSynthetic).toList();
		List<SessionEvent> conversation = view.stream().filter(e -> !e.isSynthetic() && !isStoredSystem(e)).toList();
		int firstTurn = 0;
		while (firstTurn < conversation.size() && !conversation.get(firstTurn).isTurnStart()) {
			firstTurn++;
		}
		List<SessionEvent> preamble = conversation.subList(0, firstTurn);
		List<SessionEvent> turns = conversation.subList(firstTurn, conversation.size());
		if (turns.isEmpty()) {
			return CompactionResult.unchanged();
		}
		List<SessionEvent> pinned = new ArrayList<>(pinnedSystem);
		if (this.summarizer == null) {
			pinned.addAll(priorSummary);
		}
		pinned.addAll(preamble);

		// 4. The cut, on a turn start, keeping the newest turn
		int rawCut = rawCut(turns, pinned, tokens);
		if (rawCut <= 0) {
			return CompactionResult.unchanged();
		}
		int cut = snapToTurnStart(turns, rawCut);
		if (cut == turns.size()) {
			cut = lastTurnStart(turns);
		}
		if (cut == 0) {
			return CompactionResult.unchanged();
		}
		List<SessionEvent> covered = turns.subList(0, cut);
		List<SessionEvent> preserved = turns.subList(cut, turns.size());

		// 5. The summary that stands in for the covered turns, if any
		List<Message> summary = List.of();
		int summaryTokens = 0;
		if (this.summarizer != null) {
			summary = this.summarizer
				.summarize(new SummaryRequest(request.session(), priorSummary, covered, preserved));
			if (summary.isEmpty()) {
				return CompactionResult.unchanged();
			}
			summaryTokens = summary.stream().mapToInt(m -> this.tokenCountEstimator.estimate(EventText.of(m))).sum();
		}
		return result(view, pinned, preserved.get(0), summary, summaryTokens, tokens);
	}

	/** The index into the turns of the first event the window keeps, or 0 to keep all. */
	private int rawCut(List<SessionEvent> turns, List<SessionEvent> pinned, ToIntFunction<SessionEvent> tokens) {
		int size = this.window.size();
		return switch (this.window.rule()) {
			case LAST_EVENTS -> Math.max(0, turns.size() - size);
			case LAST_TURNS -> {
				int seen = 0;
				for (int i = turns.size() - 1; i >= 0; i--) {
					if (turns.get(i).isTurnStart() && ++seen == size) {
						yield (i == 0) ? 0 : i;
					}
				}
				yield 0;
			}
			case TOKEN_BUDGET -> {
				// Pinned events are sent to the model too, so their cost comes off the
				// budget first; the walk stops at the first event that does not fit, so
				// the kept window is a contiguous suffix
				int remaining = size - pinned.stream().mapToInt(tokens).sum();
				int used = 0;
				int cut = turns.size();
				for (int i = turns.size() - 1; i >= 0; i--) {
					int cost = tokens.applyAsInt(turns.get(i));
					if (used + cost > remaining) {
						break;
					}
					used += cost;
					cut = i;
				}
				yield cut;
			}
		};
	}

	private static int snapToTurnStart(List<SessionEvent> turns, int index) {
		int i = index;
		while (i < turns.size() && !turns.get(i).isTurnStart()) {
			i++;
		}
		return i;
	}

	private static int lastTurnStart(List<SessionEvent> turns) {
		for (int i = turns.size() - 1; i >= 0; i--) {
			if (turns.get(i).isTurnStart()) {
				return i;
			}
		}
		return 0;
	}

	/**
	 * Partitions the view: the pinned events first; every other event before the first
	 * preserved one, and every superseded stored system message wherever it sits, is
	 * covered; the rest is preserved in log order.
	 */
	private static CompactionResult result(List<SessionEvent> view, List<SessionEvent> pinned,
			SessionEvent firstPreserved, List<Message> summary, int summaryTokens, ToIntFunction<SessionEvent> tokens) {
		Set<SessionEvent> pinnedSet = new HashSet<>(pinned);
		int preservedFrom = view.indexOf(firstPreserved);
		List<SessionEvent> covered = new ArrayList<>();
		List<SessionEvent> preserved = new ArrayList<>();
		for (int i = 0; i < view.size(); i++) {
			SessionEvent event = view.get(i);
			if (pinnedSet.contains(event)) {
				continue;
			}
			(i < preservedFrom || isStoredSystem(event) ? covered : preserved).add(event);
		}
		if (covered.isEmpty()) {
			return CompactionResult.unchanged();
		}
		int saved = covered.stream().mapToInt(tokens).sum() - summaryTokens;
		return new CompactionResult(covered, pinned, preserved, summary, Math.max(0, saved), STRATEGY_NAME);
	}

	/** A system message the application stored: not a synthetic one a compaction left. */
	static boolean isStoredSystem(SessionEvent event) {
		return event.hasMessage() && !event.isSynthetic() && event.getMessageType() == MessageType.SYSTEM;
	}

	/**
	 * The latest stored system message, as a list of at most one event: "latest wins".
	 */
	static List<SessionEvent> latestStoredSystem(List<SessionEvent> view) {
		for (int i = view.size() - 1; i >= 0; i--) {
			if (isStoredSystem(view.get(i))) {
				return List.of(view.get(i));
			}
		}
		return List.of();
	}

	public static final class Builder {

		private CompactionWindow window = DEFAULT_WINDOW;

		private TokenCountEstimator tokenCountEstimator = new JTokkitTokenCountEstimator();

		@Nullable private CompactionSummarizer summarizer;

		private Builder() {
		}

		/** The rule to cut by; {@link #DEFAULT_WINDOW} by default. */
		public Builder window(CompactionWindow window) {
			Assert.notNull(window, "window must not be null");
			this.window = window;
			return this;
		}

		/**
		 * Keeps the last {@code n} conversation events; see
		 * {@link CompactionWindow#lastEvents}.
		 */
		public Builder lastEvents(int n) {
			return window(CompactionWindow.lastEvents(n));
		}

		/** Keeps the last {@code n} turns; see {@link CompactionWindow#lastTurns}. */
		public Builder lastTurns(int n) {
			return window(CompactionWindow.lastTurns(n));
		}

		/**
		 * Keeps what fits in {@code maxTokens}; see {@link CompactionWindow#tokenBudget}.
		 */
		public Builder tokenBudget(int maxTokens) {
			return window(CompactionWindow.tokenBudget(maxTokens));
		}

		/**
		 * The estimator of a token budget and of the tokens a pass saves, applied to
		 * {@link EventText}; a {@link JTokkitTokenCountEstimator} by default.
		 */
		public Builder tokenCountEstimator(TokenCountEstimator tokenCountEstimator) {
			Assert.notNull(tokenCountEstimator, "tokenCountEstimator must not be null");
			this.tokenCountEstimator = tokenCountEstimator;
			return this;
		}

		/**
		 * The summarizer that condenses the covered turns into a summary turn; none by
		 * default, so the covered turns leave the view without a stand-in.
		 */
		public Builder summarizer(CompactionSummarizer summarizer) {
			Assert.notNull(summarizer, "summarizer must not be null");
			this.summarizer = summarizer;
			return this;
		}

		public WindowCompactionStrategy build() {
			return new WindowCompactionStrategy(this);
		}

	}

}
