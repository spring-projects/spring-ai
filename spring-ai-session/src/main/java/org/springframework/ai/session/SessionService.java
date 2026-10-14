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
 * Primary API for managing the full lifecycle of {@link Session} objects.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public interface SessionService {

	// Sessions

	/**
	 * Creates a new session. Uses {@link CreateSessionRequest#id()} when set, otherwise a
	 * random UUID.
	 * @throws SessionAlreadyExistsException if a session with the requested id already
	 * exists
	 */
	Session create(CreateSessionRequest request);

	@Nullable Session findById(String sessionId);

	/**
	 * Returns the sessions whose metadata holds {@code value} under {@code name}; see
	 * {@link SessionRepository#findByIndex}. The sessions of a principal are
	 * {@code findByIndex(SessionAttributes.PRINCIPAL_NAME, name)}.
	 */
	List<Session> findByIndex(String name, String value);

	/** Deletes the session and its events. */
	void delete(String sessionId);

	/**
	 * Deletes all sessions whose {@code expiresAt} is before {@code before}. Delegates to
	 * {@link SessionRepository#deleteExpiredSessions(Instant)}, which does not delete a
	 * session whose TTL was extended concurrently. Returns the number of sessions
	 * deleted.
	 * <p>
	 * This method does not schedule itself — call it from a {@code @Scheduled} method, a
	 * Quartz job, or any other scheduler: <pre>{@code
	 * &#64;Scheduled(fixedRate = 3_600_000) // every hour
	 * void sweepExpiredSessions() {
	 *     sessionService.deleteExpiredSessions(Instant.now());
	 * }
	 * }</pre>
	 */
	int deleteExpiredSessions(Instant before);

	// Events

	/**
	 * Appends a {@link SessionEvent} to the session identified by
	 * {@link SessionEvent#getSessionId()}.
	 * @throws IllegalArgumentException if the event wraps a
	 * {@link org.springframework.ai.chat.messages.SystemMessage} and the service does not
	 * allow storing system messages (the default for {@link DefaultSessionService}; see
	 * {@link DefaultSessionService.Builder#allowSystemMessages(boolean)})
	 */
	void appendEvent(SessionEvent event);

	/**
	 * Convenience shorthand: wraps the message in a {@link SessionEvent} and appends it.
	 * Subject to the same system-message rule as {@link #appendEvent(SessionEvent)}.
	 */
	default void appendMessage(String sessionId, Message message) {
		appendEvent(SessionEvent.builder().sessionId(sessionId).message(message).build());
	}

	/** Returns events matching the given filter, in chronological order. */
	List<SessionEvent> getEvents(String sessionId, EventFilter filter);

	/** Returns all events for the session, in chronological order. */
	default List<SessionEvent> getEvents(String sessionId) {
		return getEvents(sessionId, EventFilter.all());
	}

	/**
	 * Convenience: returns the messages of <em>all</em> events as a flat {@link Message}
	 * list, including the events a compaction covered and any synthetic summaries. This
	 * is the full recorded history, not a prompt: after compaction it contains both the
	 * verbatim covered turns and their summary. Events that are not messages (a
	 * compaction boundary and the like) are skipped. Use
	 * {@link #getActiveMessages(String)} for the context window to send to an LLM.
	 */
	default List<Message> getMessages(String sessionId) {
		return getEvents(sessionId).stream().filter(SessionEvent::hasMessage).map(SessionEvent::getMessage).toList();
	}

	/**
	 * Convenience: returns the messages of the <em>active</em> context window — the
	 * events a compaction covered are excluded, synthetic summaries are included — as a
	 * flat {@link Message} list, suitable for passing directly to an LLM. Equivalent to
	 * {@code getEvents(sessionId, EventFilter.active())} mapped to messages.
	 */
	default List<Message> getActiveMessages(String sessionId) {
		return getEvents(sessionId, EventFilter.active()).stream().map(SessionEvent::getMessage).toList();
	}

	// Compaction

	/**
	 * Evaluates the trigger and, if it fires, compacts the session's active view using
	 * the given strategy. Reads the view once, builds a
	 * {@link org.springframework.ai.session.compaction.CompactionRequest}, checks the
	 * trigger, and — only if the trigger fires — runs the strategy and records its
	 * decision by appending a {@link EventType#COMPACTION} boundary event followed by the
	 * summary events. The log is never changed: the events the compaction covers stay
	 * where they are and leave the active view, which is projected from the newest
	 * boundary. No-ops (trigger does not fire, or the strategy covers nothing) skip the
	 * repository write entirely.
	 * <p>
	 * Pass {@code (req) -> true} as the trigger to compact unconditionally.
	 * @return the compaction result;
	 * {@link org.springframework.ai.session.compaction.CompactionResult#isUnchanged()}
	 * when the trigger did not fire, the strategy covered nothing, or a concurrent writer
	 * changed the log first
	 */
	CompactionResult compact(String sessionId, CompactionTrigger trigger, CompactionStrategy strategy);

}
