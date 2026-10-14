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
 * Persistence contract for {@link Session} objects and their event logs.
 *
 * <p>
 * Implementations must be thread-safe. Events are stored separately from session
 * metadata. The event log is append-only: nothing ever updates, moves or removes an
 * event, and compaction is recorded by appending (see {@link #appendEvents}).
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
	 * Inserts the session only if no session with the same id exists — never updates an
	 * existing one. Used by {@link SessionService#create(CreateSessionRequest)} so that
	 * two concurrent creates of the same id cannot both succeed (the second would
	 * otherwise silently take over the first caller's session).
	 * <p>
	 * Implementations must perform the check and the insert atomically (e.g. a
	 * primary-key-guarded {@code INSERT}, or a compare-and-set put). The session is
	 * stored without a run, as with {@link #save}.
	 * @return {@code true} if the session was inserted, {@code false} if a session with
	 * the same id already exists (the existing session is left untouched)
	 */
	boolean saveIfAbsent(Session session);

	@Nullable Session findById(String sessionId);

	/**
	 * Returns the sessions whose {@link Session#metadata() metadata} holds {@code value}
	 * under {@code name}, in a stable store-defined order (creation time, then id).
	 * <p>
	 * This is the one scoped lookup of the store, in the shape of Spring Session's
	 * {@code findByIndexNameAndIndexValue}: the store indexes attributes, it has no
	 * notion of ownership. Every store indexes {@link SessionAttributes#PRINCIPAL_NAME};
	 * a store that cannot index every key documents which other keys it answers for.
	 * @param name the metadata key
	 * @param value the value, compared as a string
	 */
	List<Session> findByIndex(String name, String value);

	/**
	 * Deletes the session with the given ID.
	 */
	void delete(String sessionId);

	/**
	 * Deletes every session whose TTL has expired before the given instant, together with
	 * its events, and returns the number of sessions deleted.
	 * <p>
	 * Implementations must check the expiry and delete in one atomic step (e.g. a single
	 * {@code DELETE … WHERE expires_at < ?}), so that a session whose TTL was extended
	 * concurrently (e.g. by {@link #save(Session)}) is not deleted.
	 * @param before the expiry cut-off
	 * @return the number of sessions deleted
	 */
	int deleteExpiredSessions(Instant before);

	// Events

	/**
	 * Appends a single event to the session's event log. The target session is identified
	 * by {@link SessionEvent#getSessionId()}.
	 * <p>
	 * <strong>Idempotent by id:</strong> if an event with the same
	 * {@link SessionEvent#getId()} already exists for this session, this call is a no-op
	 * — it does not throw, and it does not append a duplicate row or increment the
	 * event-log version. This makes a retried append (e.g. after a crash between the
	 * write and the caller receiving confirmation) safe to repeat. Callers that want this
	 * safety should supply a deterministic id (e.g. derived from a durable run/turn id)
	 * rather than relying on {@link SessionEvent.Builder}'s random default.
	 * <p>
	 * Event ids should be unique across <em>all</em> sessions: persistent implementations
	 * (e.g. JDBC) key events by id alone and reject an id already used by a different
	 * session rather than silently dropping the event.
	 * @throws IllegalArgumentException if the session does not exist
	 * @throws IllegalStateException if the id is already used by an event of another
	 * session (implementations with a global event-id namespace only)
	 */
	void appendEvent(SessionEvent event);

	/**
	 * Atomically appends several events to the session's event log, in order, using an
	 * optimistic compare-and-swap: the events are appended only if the current event-log
	 * version equals {@code expectedVersion}; otherwise nothing is written and
	 * {@code false} is returned (another writer changed the log between the caller's read
	 * and this write). On success the version is incremented once, whatever the number of
	 * events.
	 *
	 * <p>
	 * This is how compaction is recorded: {@link SessionService#compact} appends one
	 * {@link EventType#COMPACTION} boundary event followed by its summary events. The log
	 * is append-only; compaction never flags, moves or removes an existing event. The
	 * boundary defines the active view from then on (see
	 * {@link org.springframework.ai.session.compaction.CompactionBoundary#project(List)}),
	 * and the version check keeps two concurrent compactions from both landing.
	 *
	 * <p>
	 * Callers should read {@link #getEventVersion} <em>before</em> reading events via
	 * {@link #findEvents}, then pass that version here. If this method returns
	 * {@code false} the caller should treat the write as not done — the concurrent writer
	 * already handled the session.
	 * @param sessionId the session whose log is appended to; every event must belong to
	 * it
	 * @param events the events, in the order they are appended; at least one, and their
	 * ids must be new
	 * @param expectedVersion the event-log version the caller observed
	 * @return {@code true} when the events were appended, {@code false} on a version
	 * mismatch
	 * @throws IllegalArgumentException if the session does not exist, {@code events} is
	 * empty, an event belongs to another session, or an event id is already in the log or
	 * appears twice in the batch
	 */
	boolean appendEvents(String sessionId, List<SessionEvent> events, long expectedVersion);

	/**
	 * Returns the current event-log version for the given session. The version is
	 * incremented atomically on every {@link #appendEvent} call that actually appends a
	 * new event, and on every successful {@link #appendEvents} call (an idempotent replay
	 * of an already-applied {@link #appendEvent} does not increment it). Returns
	 * {@code 0} when the session does not exist or has no events yet.
	 * <p>
	 * Read this <em>before</em> calling {@link #findEvents} to obtain a version that is
	 * guaranteed to be ≤ the version of the events you subsequently read, which is the
	 * safe ordering for passing to {@link #appendEvents(String, List, long)}.
	 */
	long getEventVersion(String sessionId);

	/**
	 * Returns events for the given session that match the provided filter. Events are
	 * always returned in chronological order (oldest first).
	 * <p>
	 * <strong>Windows keep turns whole.</strong> With an {@link EventFilter.Window.Last}
	 * window the most recent N matching events are returned, extended back to the start
	 * of the turn the window lands in (the nearest preceding
	 * {@link SessionEvent#isTurnStart() USER event} among the matching events), so a tool
	 * call is never returned without its results. The extension is skipped when no turn
	 * start precedes the window. {@link EventFilter#applyWindow(List)} is the reference
	 * implementation; {@code JdbcSessionRepository} pushes it down as one bounded extra
	 * query when the window lands mid-turn.
	 * <p>
	 * <strong>The active view.</strong> With {@link EventFilter#activeView()} the filter
	 * applies to the active view of the log rather than the whole log: the events named
	 * by the newest {@link EventType#COMPACTION} boundary (pinned, summary, preserved, in
	 * that order) and everything appended after it. A log without a boundary is its own
	 * view.
	 * {@link org.springframework.ai.session.compaction.CompactionBoundary#project(List)}
	 * is the reference implementation; a store may fetch only the view's rows (the named
	 * ids and the rows from the boundary on) and hand them to
	 * {@link EventFilter#apply(List)}, as {@code JdbcSessionRepository} does. The view is
	 * bounded by compaction, so it is read whole and windowed in memory.
	 * <p>
	 * <strong>Push the filter down.</strong> For a read of the whole log the cost must
	 * not grow with its size: an implementation is expected to evaluate
	 * {@link EventFilter#excludeSynthetic()}, {@link EventFilter#eventTypes()},
	 * {@link EventFilter#messageTypes()} and the {@link EventFilter#window()} in its
	 * query, and to fall back to {@link EventFilter#apply(List)} only for what the store
	 * cannot express, as {@code JdbcSessionRepository} does.
	 * <p>
	 * <strong>Existence contract:</strong> returns an empty list when the session does
	 * not exist, rather than throwing. This differs from {@link #appendEvent} and
	 * {@link #appendEvents}, which throw {@link IllegalArgumentException} for unknown
	 * sessions. The silent-empty behaviour allows callers to query event history without
	 * first checking whether the session exists (the "read before write" pattern used by
	 * {@code SessionMemoryAdvisor}).
	 */
	List<SessionEvent> findEvents(String sessionId, EventFilter filter);

}
