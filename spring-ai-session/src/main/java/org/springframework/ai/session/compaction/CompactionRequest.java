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

import java.util.List;

import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionEvent;
import org.springframework.util.Assert;

/**
 * Contextual information provided to {@link CompactionTrigger} and
 * {@link CompactionStrategy} when evaluating whether compaction is needed.
 *
 * @param session the session being evaluated for compaction
 * @param events the session's current list of events, ordered from oldest to newest
 * @param currentTurnCount total number of turns in the session (a turn is a user message
 * plus all subsequent events up to the next user message)
 * @author Christian Tzolov
 * @since 2.1.0
 */
public record CompactionRequest(Session session, List<SessionEvent> events, int currentTurnCount) {

	/**
	 * Creates a {@code CompactionRequest} from the given session and its event list.
	 */
	public static CompactionRequest of(Session session, List<SessionEvent> events) {
		Assert.notNull(session, "session must not be null");
		Assert.notNull(events, "events must not be null");
		// Count only non-synthetic USER messages: a synthetic summary turn's shadow
		// prompt
		// is not a real turn.
		int turnCount = (int) events.stream().filter(e -> !e.isSynthetic()).filter(SessionEvent::isTurnStart).count();
		return new CompactionRequest(session, events, turnCount);
	}

}
