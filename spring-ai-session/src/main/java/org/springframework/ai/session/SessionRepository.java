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

/**
 * The store of sessions and their logs. Implementations are thread-safe. The log is
 * append-only: nothing updates, moves or removes an event, and a compaction is recorded
 * by appending (see {@link #appendEvents}).
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public interface SessionRepository {

	// Sessions

	/**
	 * Persists session metadata (create or update). If the session already exists its
	 * event log and its original {@link Session#createdAt()} are preserved; the other
	 * metadata fields are replaced.
	 * @return the saved session
	 */
	Session save(Session session);

	/**
	 * Inserts the session unless one with the same id exists, atomically: a
	 * primary-key-guarded {@code INSERT}, or a compare-and-set put.
	 * {@link SessionService#create} relies on it, so two concurrent creates of one id
	 * cannot both succeed.
	 * @return {@code true} if the session was inserted, {@code false} if one with the
	 * same id exists; the existing session is left untouched
	 */
	boolean saveIfAbsent(Session session);

	@Nullable Session findById(String sessionId);

	/**
	 * The sessions whose {@link Session#metadata() metadata} holds {@code value} under
	 * {@code name}, oldest first (creation time, then id). The one scoped lookup of the
	 * store, in the shape of Spring Session's {@code findByIndexNameAndIndexValue}: the
	 * store indexes attributes and has no notion of ownership. Every store indexes
	 * {@link SessionAttributes#PRINCIPAL_NAME}; a store that cannot index every key
	 * documents which others it answers for.
	 * @param name the metadata key
	 * @param value the value, compared as a string
	 */
	List<Session> findByMetadata(String name, String value);

	/** Deletes the session and its events. */
	void delete(String sessionId);

	/**
	 * Deletes every session that expired before the given instant, with its events, in
	 * one atomic step per session, so a session whose expiry was extended concurrently
	 * ({@link #save}) is kept.
	 * @param before the expiry cut-off
	 * @return the number of sessions deleted
	 */
	int deleteExpiredSessions(Instant before);

	// Events

	/**
	 * Appends one event to the log of the session named by
	 * {@link SessionEvent#getSessionId()}.
	 * <p>
	 * Idempotent by id: an event whose id is already in the session's log is a retried
	 * append and a no-op, neither a duplicate nor an error, and the version is not
	 * advanced. A caller that wants to retry safely supplies its own id rather than the
	 * builder's random one. Event ids are unique across sessions: a persistent store keys
	 * events by id alone and rejects an id used by another session rather than dropping
	 * the event.
	 * @throws IllegalArgumentException if the session does not exist
	 * @throws IllegalStateException if the id is used by an event of another session
	 */
	void appendEvent(SessionEvent event);

	/**
	 * Appends several events in order, atomically, only if the log's version equals
	 * {@code expectedVersion}; otherwise nothing is written and {@code false} is
	 * returned. On success the version advances once, whatever the number of events. This
	 * is how a compaction is recorded: one {@link EventType#COMPACTION} boundary and its
	 * summary events, with the version check keeping two concurrent compactions from both
	 * landing. Read {@link #getEventVersion} before {@link #findEvents}, then pass that
	 * version here.
	 * @param sessionId the session whose log is appended to; every event must belong to
	 * it
	 * @param events the events, in order; at least one, with ids that are new to the log
	 * @param expectedVersion the version the caller read
	 * @return {@code true} when the events were appended, {@code false} when the version
	 * did not match
	 * @throws IllegalArgumentException if the session does not exist, the batch is empty,
	 * an event belongs to another session, or an id is already in the log or appears
	 * twice
	 */
	boolean appendEvents(String sessionId, List<SessionEvent> events, long expectedVersion);

	/**
	 * The version of the session's log, advanced on every {@link #appendEvent} that
	 * appends a new event and on every successful {@link #appendEvents}; {@code 0} for a
	 * session that does not exist or has no events. Read it before {@link #findEvents},
	 * so that the version passed to {@link #appendEvents} is not newer than the events
	 * read.
	 */
	long getEventVersion(String sessionId);

	/**
	 * The events of the session the filter selects, oldest first; an empty list for a
	 * session that does not exist.
	 * <p>
	 * A {@link EventFilter.Window.Last} window returns the newest N matching events,
	 * extended back to the start of the turn it lands in (the nearest preceding
	 * {@link SessionEvent#isTurnStart() turn start} among the matches), so a tool call is
	 * never returned without its results; the extension is skipped when no turn start
	 * precedes the window. With {@link EventFilter#activeView()} the filter applies to
	 * the active view, the events the newest {@link EventType#COMPACTION} boundary names
	 * and everything appended after it, rather than to the whole log.
	 * <p>
	 * {@link EventFilter#apply(List)} is the reference implementation of both. A store
	 * pushes the criteria and the window down to its query, so that a read does not cost
	 * the whole log, and may fetch only the rows a view can contain and hand them to
	 * {@code apply}, as {@code JdbcSessionRepository} does.
	 */
	List<SessionEvent> findEvents(String sessionId, EventFilter filter);

}
