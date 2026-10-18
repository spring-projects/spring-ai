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
import org.springframework.ai.session.status.TurnService;
import org.springframework.util.Assert;

/**
 * The sessions of an application and their conversations: creating and finding sessions,
 * appending and reading their events, compacting them, and running turns on them through
 * {@link #turns()}. {@link DefaultSessionService} implements it on a
 * {@link SessionRepository}.
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

	/**
	 * The session with the request's id, created from the request when there is none.
	 * Safe under concurrent first use: when another caller creates it first, the stored
	 * session is returned, not this request's. The default finds, creates, and on a
	 * {@link SessionAlreadyExistsException} finds again, through this service's own
	 * {@link #findById} and {@link #create}.
	 * @throws IllegalArgumentException if the request has no id, null or blank
	 */
	default Session findOrCreate(CreateSessionRequest request) {
		Assert.notNull(request, "request must not be null");
		String sessionId = request.id();
		Assert.hasText(sessionId, "request must have an id");
		Session session = findById(sessionId);
		if (session != null) {
			return session;
		}
		try {
			return create(request);
		}
		catch (SessionAlreadyExistsException ex) {
			Session existing = findById(sessionId);
			if (existing == null) {
				throw ex;
			}
			return existing;
		}
	}

	@Nullable Session findById(String sessionId);

	/**
	 * The sessions whose metadata holds {@code value} under {@code name}, oldest first,
	 * for the keys the store indexes; see {@link SessionRepository#findByMetadata}. The
	 * sessions of a principal are {@code findByMetadata(SessionAttributes.PRINCIPAL_NAME,
	 * name)}.
	 */
	List<Session> findByMetadata(String name, String value);

	/** Deletes the session and its events. */
	void delete(String sessionId);

	/**
	 * Deletes every session that expired before the given instant, with their events, and
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
	 * Appends several events in order, atomically, but only if the log's version still
	 * equals {@code expectedVersion}. Otherwise nothing is written and {@code false} is
	 * returned: another writer appended in between. This is the compare-and-set of the
	 * log, as {@link SessionRepository#appendEvents} defines it. Read
	 * {@link #getEventVersion}, then read whatever the write depends on, then write; a
	 * {@code false} means read again. The compaction and the turn status are recorded
	 * this way.
	 * @param sessionId the session
	 * @param events the events to append, oldest first; must not be empty
	 * @param expectedVersion the version read before the events were prepared
	 * @return {@code true} when the events were appended, {@code false} when the log
	 * moved on and nothing was written
	 * @throws IllegalArgumentException if the session does not exist or the batch is
	 * empty, and for a system message among the events as {@link #appendEvent} throws it
	 */
	boolean appendEvents(String sessionId, List<SessionEvent> events, long expectedVersion);

	/**
	 * Returns the version of the session's log. Every append advances it, so it tells
	 * whether the log changed between two reads; {@code 0} for a session without events,
	 * or one that does not exist. Read it before the events a version-checked
	 * {@link #appendEvents} depends on.
	 */
	long getEventVersion(String sessionId);

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

	// Turns

	/**
	 * Returns the turns of the sessions: the {@link TurnService} that starts, ends,
	 * interrupts, parks and resumes a turn and reads its status, all on this service.
	 * Every harness runs its turns through it, so a turn started on one instance can be
	 * interrupted from another, and two harnesses never run one session at once. The
	 * status is a projection of the log; the turn service keeps no state, so the returned
	 * instance may be kept or taken again. <pre>{@code
	 * TurnStatus turn = sessionService.turns().start(sessionId, runId, Duration.ofMinutes(30));
	 * }</pre>
	 */
	default TurnService turns() {
		return new TurnService(this);
	}

	// Compaction

	/**
	 * Evaluates the trigger on the active view and, when it fires, runs the strategy and
	 * records its result: one {@link EventType#COMPACTION} boundary event followed by the
	 * summary events, in one version-checked append. The log is never changed; the
	 * boundary defines the view from then on. An append that lands in between does not
	 * waste the summary: the new events join the view verbatim and the write is retried,
	 * a bounded number of times. Pass {@code request -> true} to compact unconditionally.
	 * @return the result as written, the events appended meanwhile added to its preserved
	 * tail;
	 * {@link org.springframework.ai.session.compaction.CompactionResult#isUnchanged()}
	 * when nothing was written: the trigger did not fire, the strategy covered nothing,
	 * another compaction landed first, or appends kept overtaking the write
	 */
	CompactionResult compact(String sessionId, CompactionTrigger trigger, CompactionStrategy strategy);

}
