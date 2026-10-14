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

import org.springframework.util.Assert;

/**
 * The rule a {@link WindowCompactionStrategy} cuts by: how much of the recent
 * conversation stays in the view, measured in events, in turns or in estimated tokens.
 * The cut it produces always falls on a turn start, so a turn is never split, and the
 * newest turn stays whatever its size.
 *
 * @param rule what the size counts
 * @param size the number of events or turns kept, or the token budget of the view
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record CompactionWindow(Rule rule, int size) {

	public CompactionWindow {
		Assert.notNull(rule, "rule must not be null");
		Assert.isTrue(size > 0, "size must be greater than 0");
	}

	/** Keeps the last {@code n} conversation events. */
	public static CompactionWindow lastEvents(int n) {
		return new CompactionWindow(Rule.LAST_EVENTS, n);
	}

	/**
	 * Keeps the last {@code n} turns: a user message and everything up to the next one.
	 */
	public static CompactionWindow lastTurns(int n) {
		return new CompactionWindow(Rule.LAST_TURNS, n);
	}

	/**
	 * Keeps what fits in {@code maxTokens}, counted from the newest event back: the
	 * pinned events come off the budget first, and the kept window is a contiguous
	 * suffix.
	 */
	public static CompactionWindow tokenBudget(int maxTokens) {
		return new CompactionWindow(Rule.TOKEN_BUDGET, maxTokens);
	}

	/** What the size of a window counts. */
	public enum Rule {

		/** The last {@code size} conversation events. */
		LAST_EVENTS,

		/** The last {@code size} turns. */
		LAST_TURNS,

		/** The newest events whose estimated tokens fit in {@code size}. */
		TOKEN_BUDGET

	}

}
