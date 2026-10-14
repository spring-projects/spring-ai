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
 * Triggers compaction when the session exceeds a maximum number of <em>turns</em>.
 *
 * <p>
 * A <strong>turn</strong> is defined as one user message plus all subsequent events
 * (assistant replies, tool calls, tool results) up to the next user message. Counting
 * turns is more semantically meaningful than counting raw messages because it represents
 * complete user↔agent exchanges and ensures compaction never fires in the middle of a
 * multi-step tool interaction.
 *
 * <p>
 * Pair with a {@link CompactionWindow#lastTurns turn window} to keep the threshold and
 * the compaction unit consistent.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 * @see CompactionWindow#lastTurns(int)
 */
public final class TurnCountTrigger implements CompactionTrigger {

	private final int maxTurns;

	/**
	 * @param maxTurns maximum number of turns before compaction is triggered. Must be
	 * positive.
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
