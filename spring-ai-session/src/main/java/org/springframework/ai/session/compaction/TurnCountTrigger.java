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
 * Fires when the view holds more than a number of turns: a user message and everything up
 * to the next one. Counting turns rather than events follows the shape of the
 * conversation, and never fires in the middle of a tool-calling exchange. Pair it with a
 * {@link CompactionWindow#lastTurns turn window}, so the threshold and the window count
 * the same thing.
 *
 * @see CompactionWindow#lastTurns(int)
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class TurnCountTrigger implements CompactionTrigger {

	private final int maxTurns;

	/**
	 * @param maxTurns the number of turns the view may hold before compaction; positive
	 */
	public TurnCountTrigger(int maxTurns) {
		Assert.isTrue(maxTurns > 0, "maxTurns must be greater than 0");
		this.maxTurns = maxTurns;
	}

	@Override
	public boolean shouldCompact(CompactionRequest request) {
		return request.currentTurnCount() > this.maxTurns;
	}

	public int getMaxTurns() {
		return this.maxTurns;
	}

}
