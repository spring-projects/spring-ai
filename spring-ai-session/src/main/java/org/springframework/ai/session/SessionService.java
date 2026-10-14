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
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.session.compaction.CompactionResult;
import org.springframework.ai.session.compaction.CompactionStrategy;
import org.springframework.ai.session.compaction.CompactionTrigger;

/**
 * The sessions of an application and their conversations: creating and finding sessions,
 * appending and reading their events, compacting them. {@link DefaultSessionService}
 * implements it on a {@link SessionRepository}.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public interface SessionService {

	// Sessions

	/**
	 * Creates a session with the request's id, a random UUID when it has none, its time
	 * to live and its metadata.
	 * @throws SessionAlreadyExistsException if a session with the requested id exists
	 */
	Session create(CreateSessionRequest request);

	@Nullable Session findById(String sessionId);

	/**
	 * The sessions whose metadata holds {@code value} under {@code name}, oldest first;
	 * see {@link SessionRepository#findByIndex}. The sessions of a principal are {@code
	 * findByIndex(SessionAttributes.PRINCIPAL_NAME, name)}.
	 */
	List<Session> findByIndex(String name, String value);

	/** Deletes the session and its events. */
	void delete(String sessionId);

	/**
	 * Deletes every session that expired before the given instant, with its events, and
	 * returns how many. Nothing calls this on its own: call it from a scheduler, every
	 * hour for example.
	 */
	int deleteExpiredSessions(Instant before);

	// Events

	/**
	 * Appends an event to the session named by {@link SessionEvent#getSessionId()}.
	 * @throws IllegalArgumentException if the event wraps a
	 * {@link org.springframework.ai.chat.messages.SystemMessage} and the service does not
	 * allow storing system messages; see
	 * {@link DefaultSessionService.Builder#allowSystemMessages(boolean)}
	 */
	void appendEvent(SessionEvent event);

	/**
	 * Wraps the message in a {@link SessionEvent} and appends it; see
	 * {@link #appendEvent(SessionEvent)}.
	 */
	default void appendMessage(String sessionId, Message message) {
		appendEvent(SessionEvent.builder().sessionId(sessionId).message(message).build());
	}

	/** The events the filter selects, oldest first; see {@link EventFilter}. */
	List<SessionEvent> getEvents(String sessionId, EventFilter filter);

	/** Every event of the log, oldest first. */
	default List<SessionEvent> getEvents(String sessionId) {
		return getEvents(sessionId, EventFilter.all());
	}

	/**
	 * The messages of the whole log, the events a compaction covered and the summaries
	 * included: the full history, not a prompt. Use {@link #getActiveMessages(String)}
	 * for the prompt.
	 */
	default List<Message> getMessages(String sessionId) {
		return getEvents(sessionId).stream().filter(SessionEvent::hasMessage).map(SessionEvent::getMessage).toList();
	}

	/**
	 * The messages of the active view, the context window to send to the model: {@code
	 * getEvents(sessionId, EventFilter.active())} as messages.
	 */
	default List<Message> getActiveMessages(String sessionId) {
		return getEvents(sessionId, EventFilter.active()).stream().map(SessionEvent::getMessage).toList();
	}

	// Compaction

	/**
	 * Evaluates the trigger on the active view and, when it fires, runs the strategy and
	 * records its result: one {@link EventType#COMPACTION} boundary event followed by the
	 * summary events, in one version-checked append. The log is never changed; the
	 * boundary defines the view from then on. Pass {@code request -> true} to compact
	 * unconditionally.
	 * @return the result;
	 * {@link org.springframework.ai.session.compaction.CompactionResult#isUnchanged()}
	 * when nothing was written: the trigger did not fire, the strategy covered nothing,
	 * or a concurrent writer changed the log first
	 */
	CompactionResult compact(String sessionId, CompactionTrigger trigger, CompactionStrategy strategy);

}
