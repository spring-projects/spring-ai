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

package org.springframework.ai.session.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.sql.DataSource;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.EventType;
import org.springframework.ai.session.Session;
import org.springframework.ai.session.SessionAttributes;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionRepository;
import org.springframework.ai.session.compaction.CompactionBoundary;
import org.springframework.ai.session.support.SessionEventCodec;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

/**
 * The {@link SessionRepository} on a relational database: {@code AI_SESSION} (id, expiry,
 * metadata JSON, event version), {@code AI_SESSION_INDEX} (the indexed metadata
 * attributes, one row per attribute) and {@code AI_SESSION_EVENT} (the log, ordered by a
 * database-assigned {@code seq}). The schema scripts are under {@code
 * classpath:org/springframework/ai/session/jdbc/}.
 * <p>
 * On every save the metadata entries whose keys are
 * {@linkplain Builder#indexedAttributes(Set) indexed}, the principal by default, are
 * copied into {@code AI_SESSION_INDEX}, on the first {@value #MAX_INDEXED_VALUE_LENGTH}
 * characters of the value, and {@link #findByMetadata} is one query over that table, with
 * the whole value checked on the sessions it returns. An event is stored in the four
 * columns {@link SessionEventCodec} defines: the event type, the message type, the
 * message text, its own column so a store can search it, and a JSON payload.
 * <p>
 * The {@code event_version} column of {@code AI_SESSION} advances on every
 * {@link #appendEvent} that appends a new event and on every successful
 * {@link #appendEvents}, which claims the version with
 * {@code UPDATE … WHERE event_version = ?} before inserting. {@link #appendEvent} looks
 * the event id up under that row lock before inserting, so a replay is a no-op without a
 * duplicate-key error, which would mark a caller's surrounding transaction rollback-only,
 * or abort it on PostgreSQL. A read of the {@link EventFilter#activeView() active view}
 * selects the newest {@code COMPACTION} row, fetches the rows it names plus every row
 * from it on, and lets {@link EventFilter#apply(List)} order, filter and window them.
 * Every mutation runs in a {@link TransactionTemplate}; the class is thread-safe as the
 * {@link DataSource} is.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class JdbcSessionRepository implements SessionRepository {

	private static final Logger logger = LoggerFactory.getLogger(JdbcSessionRepository.class);

	// @formatter:off

	private static final String SELECT_SESSION_COLUMNS =
		"SELECT s.id, s.created_at, s.expires_at, s.metadata, s.event_version FROM AI_SESSION s";

	private static final String SELECT_SESSION_BY_ID =
		SELECT_SESSION_COLUMNS + " WHERE s.id = ?";

	private static final String SELECT_SESSIONS_BY_INDEX =
		SELECT_SESSION_COLUMNS
		+ " JOIN AI_SESSION_INDEX i ON i.session_id = s.id"
		+ " WHERE i.attr_name = ? AND i.attr_value = ? ORDER BY s.created_at, s.id";

	private static final String DELETE_SESSION_INDEX =
		"DELETE FROM AI_SESSION_INDEX WHERE session_id = ?";

	private static final String INSERT_SESSION_INDEX =
		"INSERT INTO AI_SESSION_INDEX (session_id, attr_name, attr_value) VALUES (?, ?, ?)";

	// Checks the expiry in the DELETE itself, so a concurrently extended TTL is respected.
	// Events are removed by the ON DELETE CASCADE foreign key.
	private static final String DELETE_EXPIRED_SESSIONS =
		"DELETE FROM AI_SESSION WHERE expires_at IS NOT NULL AND expires_at < ?";

	private static final String DECREMENT_EVENT_VERSION =
		"UPDATE AI_SESSION SET event_version = event_version - 1 WHERE id = ?";

	private static final String DELETE_SESSION =
		"DELETE FROM AI_SESSION WHERE id = ?";

	private static final String INSERT_EVENT =
		"INSERT INTO AI_SESSION_EVENT"
		+ " (id, session_id, timestamp, event_type, message_type, message_content, payload,"
		+ "  synthetic, metadata)"
		+ " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

	private static final String INCREMENT_EVENT_VERSION =
		"UPDATE AI_SESSION SET event_version = event_version + 1 WHERE id = ?";

	private static final String CAS_INCREMENT_EVENT_VERSION =
		"UPDATE AI_SESSION SET event_version = event_version + 1"
		+ " WHERE id = ? AND event_version = ?";

	private static final String GET_EVENT_VERSION =
		"SELECT event_version FROM AI_SESSION WHERE id = ?";

	private static final String SELECT_EVENT_SESSION_ID =
		"SELECT session_id FROM AI_SESSION_EVENT WHERE id = ?";

	// The newest compaction boundary of a session, if any: it defines the active view.
	private static final String SELECT_NEWEST_BOUNDARY =
		"SELECT e.seq, e.payload FROM AI_SESSION_EVENT e"
		+ " WHERE e.session_id = ? AND e.event_type = ? ORDER BY e.seq DESC LIMIT 1";

	private static final String SELECT_EVENT_COLUMNS =
		"SELECT e.id, e.session_id, e.timestamp, e.event_type, e.message_type, e.message_content,"
		+ "       e.payload, e.synthetic, e.metadata";

	private static final String SELECT_EVENTS_BASE =
		SELECT_EVENT_COLUMNS
		+ " FROM AI_SESSION_EVENT e"
		+ " WHERE e.session_id = ? ";

	// @formatter:on

	private final JdbcTemplate jdbcTemplate;

	private final TransactionTemplate transactionTemplate;

	private final JdbcSessionRepositoryDialect dialect;

	private final SessionEventCodec codec;

	private final Set<String> indexedAttributes;

	/**
	 * The number of leading characters of a metadata value the index row holds: with four
	 * bytes per character on MySQL, the {@code (attr_name, attr_value)} index stays under
	 * InnoDB's key limit. The row is a prefix index: {@link #findByMetadata} compares the
	 * whole value on the sessions the query returns, so two values that share their first
	 * {@value #MAX_INDEXED_VALUE_LENGTH} characters are told apart.
	 */
	public static final int MAX_INDEXED_VALUE_LENGTH = 512;

	/** The metadata keys every repository indexes: the principal. */
	public static final Set<String> DEFAULT_INDEXED_ATTRIBUTES = Set.of(SessionAttributes.PRINCIPAL_NAME);

	private JdbcSessionRepository(JdbcTemplate jdbcTemplate, JdbcSessionRepositoryDialect dialect,
			PlatformTransactionManager txManager, SessionEventCodec codec, Set<String> indexedAttributes) {
		this.jdbcTemplate = jdbcTemplate;
		this.dialect = dialect;
		this.transactionTemplate = new TransactionTemplate(txManager);
		this.codec = codec;
		this.indexedAttributes = Set.copyOf(indexedAttributes);
	}

	/** The metadata keys this repository indexes; see {@link #findByMetadata}. */
	public Set<String> getIndexedAttributes() {
		return this.indexedAttributes;
	}

	// -------------------------------------------------------------------------
	// SessionRepository — session lifecycle
	// -------------------------------------------------------------------------

	@Override
	public Session save(Session session) {
		Assert.notNull(session, "session must not be null");
		this.transactionTemplate.executeWithoutResult(status -> {
			this.jdbcTemplate.update(this.dialect.getUpsertSessionSql(), session.id(), toUtc(session.createdAt()),
					toUtc(session.expiresAt()), this.codec.toJson(session.metadata()));
			this.jdbcTemplate.update(DELETE_SESSION_INDEX, session.id());
			insertIndexRows(session);
		});
		// Re-read: an update keeps the stored created_at, so the input is not what was
		// saved.
		return Objects.requireNonNull(findById(session.id()), () -> "Session vanished after save: " + session.id());
	}

	@Override
	public boolean saveIfAbsent(Session session) {
		Assert.notNull(session, "session must not be null");
		Boolean inserted = this.transactionTemplate.execute(status -> {
			try {
				if (this.jdbcTemplate.update(this.dialect.getInsertSessionIfAbsentSql(), session.id(),
						toUtc(session.createdAt()), toUtc(session.expiresAt()),
						this.codec.toJson(session.metadata())) != 1) {
					return false;
				}
			}
			catch (DuplicateKeyException ex) {
				return false;
			}
			insertIndexRows(session);
			return true;
		});
		return Boolean.TRUE.equals(inserted);
	}

	private void insertIndexRows(Session session) {
		for (String name : this.indexedAttributes) {
			Object value = session.metadata().get(name);
			if (value != null) {
				this.jdbcTemplate.update(INSERT_SESSION_INDEX, session.id(), name, indexValue(String.valueOf(value)));
			}
		}
	}

	private static String indexValue(String value) {
		if (value.length() <= MAX_INDEXED_VALUE_LENGTH) {
			return value;
		}
		// Never cut a surrogate pair in two: a lone surrogate is not text to the database
		int cut = Character.isHighSurrogate(value.charAt(MAX_INDEXED_VALUE_LENGTH - 1)) ? MAX_INDEXED_VALUE_LENGTH - 1
				: MAX_INDEXED_VALUE_LENGTH;
		return value.substring(0, cut);
	}

	@Override
	public @Nullable Session findById(String sessionId) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		List<Session> results = this.jdbcTemplate.query(SELECT_SESSION_BY_ID, new SessionRowMapper(), sessionId);
		return results.isEmpty() ? null : results.get(0);
	}

	@Override
	public List<Session> findByMetadata(String name, String value) {
		Assert.hasText(name, "name must not be null or empty");
		Assert.notNull(value, "value must not be null");
		// The index row holds a prefix of the value, so a hit is a candidate, and the
		// metadata the query loads anyway settles it
		return this.jdbcTemplate.query(SELECT_SESSIONS_BY_INDEX, new SessionRowMapper(), name, indexValue(value))
			.stream()
			.filter(session -> value.equals(String.valueOf(session.metadata().get(name))))
			.toList();
	}

	@Override
	public int deleteExpiredSessions(Instant before) {
		Assert.notNull(before, "before must not be null");
		return this.jdbcTemplate.update(DELETE_EXPIRED_SESSIONS, toUtc(before));
	}

	@Override
	public void delete(String sessionId) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		this.jdbcTemplate.update(DELETE_SESSION, sessionId);
	}

	// -------------------------------------------------------------------------
	// SessionRepository — event log
	// -------------------------------------------------------------------------

	@Override
	public void appendEvent(SessionEvent event) {
		Assert.notNull(event, "event must not be null");
		String sessionId = event.getSessionId();
		try {
			this.transactionTemplate.execute(status -> {
				// Bump the version BEFORE inserting: the UPDATE locks the session row, so
				// the
				// event's seq is only assigned once any in-flight appendEvents on this
				// session has committed. Inserting first would let a concurrent
				// compaction
				// miss the uncommitted row: its boundary would land after this event in
				// seq order without naming it, so the event would wrongly enter the view
				// as "appended since". Zero updated rows also means the session does not
				// exist.
				int updated = this.jdbcTemplate.update(INCREMENT_EVENT_VERSION, sessionId);
				if (updated == 0) {
					throw new IllegalArgumentException("Session not found: " + sessionId);
				}
				// Detect a replay before inserting, under the session row lock, instead
				// of
				// relying on a failed INSERT: a duplicate-key error would mark a caller's
				// surrounding transaction rollback-only, and on PostgreSQL abort it.
				List<String> owner = this.jdbcTemplate.queryForList(SELECT_EVENT_SESSION_ID, String.class,
						event.getId());
				if (!owner.isEmpty()) {
					if (!sessionId.equals(owner.get(0))) {
						throw new IllegalStateException("Event id '" + event.getId()
								+ "' is already used by another session; event ids must be unique across sessions");
					}
					// Idempotent replay: the event was already committed, e.g. a retried
					// append after a crash. Undo the version bump so the version counts
					// only appended events.
					this.jdbcTemplate.update(DECREMENT_EVENT_VERSION, sessionId);
					logger.debug("appendEvent: event {} already exists for session {}; idempotent replay",
							event.getId(), sessionId);
					return null;
				}
				insertEvent(event);
				return null;
			});
		}
		catch (DuplicateKeyException ex) {
			// Only reached when another session inserts the same id concurrently: a
			// same-session replay is caught by the lookup above, under this session's
			// lock. Event ids are a table-wide primary key, so the event was not stored
			// and must not be dropped silently. If the competing insert is still there,
			// report the collision; if it was rolled back, propagate the failure so the
			// caller can retry.
			List<String> owner = this.jdbcTemplate.queryForList(SELECT_EVENT_SESSION_ID, String.class, event.getId());
			if (!owner.isEmpty() && !sessionId.equals(owner.get(0))) {
				throw new IllegalStateException("Event id '" + event.getId() + "' is already used by another session; "
						+ "event ids must be unique across sessions", ex);
			}
			throw ex;
		}
	}

	@Override
	public boolean appendEvents(String sessionId, List<SessionEvent> events, long expectedVersion) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		Assert.notEmpty(events, "events must not be empty");
		Set<String> batchIds = new HashSet<>();
		for (SessionEvent event : events) {
			Assert.isTrue(sessionId.equals(event.getSessionId()),
					() -> "Event '" + event.getId() + "' belongs to session '" + event.getSessionId() + "'");
			Assert.isTrue(batchIds.add(event.getId()),
					() -> "Event id '" + event.getId() + "' appears twice in the batch");
		}
		Boolean success = this.transactionTemplate.execute(status -> {
			// Atomically claim the version slot. If another writer already changed it,
			// 0 rows are updated and we bail out without touching the event log; a
			// session
			// that does not exist at all is reported as such.
			int updated = this.jdbcTemplate.update(CAS_INCREMENT_EVENT_VERSION, sessionId, expectedVersion);
			if (updated == 0) {
				if (this.jdbcTemplate.queryForList(GET_EVENT_VERSION, Long.class, sessionId).isEmpty()) {
					throw new IllegalArgumentException("Session not found: " + sessionId);
				}
				return false;
			}
			String placeholders = placeholders(events.size());
			List<String> taken = this.jdbcTemplate.queryForList(
					"SELECT id FROM AI_SESSION_EVENT WHERE id IN (" + placeholders + ")", String.class,
					events.stream().map(SessionEvent::getId).toArray());
			if (!taken.isEmpty()) {
				throw new IllegalArgumentException("Event id '" + taken.get(0) + "' is already in the log");
			}
			batchInsertEvents(events);
			return true;
		});
		return Boolean.TRUE.equals(success);
	}

	@Override
	public long getEventVersion(String sessionId) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		List<Long> result = this.jdbcTemplate.queryForList(GET_EVENT_VERSION, Long.class, sessionId);
		return result.isEmpty() ? 0L : (result.get(0) != null ? result.get(0) : 0L);
	}

	@Override
	public List<SessionEvent> findEvents(String sessionId, EventFilter filter) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		Assert.notNull(filter, "filter must not be null");
		if (filter.activeView()) {
			return findActiveView(sessionId, filter);
		}
		return findFiltered(sessionId, filter);
	}

	/**
	 * Reads the active view of a session: the rows the newest boundary names (pinned,
	 * summary, preserved) plus every row from the boundary on, in log order, which
	 * {@link EventFilter#apply(List)} then projects into view order, filters and windows.
	 * The view is bounded by compaction, so it is read whole; so is a session without a
	 * boundary.
	 */
	private List<SessionEvent> findActiveView(String sessionId, EventFilter filter) {
		List<Map<String, @Nullable Object>> boundaries = this.jdbcTemplate.queryForList(SELECT_NEWEST_BOUNDARY,
				sessionId, EventType.COMPACTION.name());
		StringBuilder sql = new StringBuilder(SELECT_EVENTS_BASE);
		List<Object> params = new ArrayList<>();
		params.add(sessionId);
		if (!boundaries.isEmpty()) {
			Map<String, @Nullable Object> boundary = boundaries.get(0);
			long boundarySeq = ((Number) Objects.requireNonNull(boundary.get("seq"))).longValue();
			Map<String, Object> payload = this.codec
				.fromJsonMap(Objects.requireNonNull((String) boundary.get("payload"), "payload"));
			List<String> named = new ArrayList<>();
			for (String key : List.of(CompactionBoundary.PAYLOAD_PINNED, CompactionBoundary.PAYLOAD_SUMMARY,
					CompactionBoundary.PAYLOAD_PRESERVED)) {
				if (payload.get(key) instanceof List<?> ids) {
					ids.forEach(id -> named.add(String.valueOf(id)));
				}
			}
			sql.append("AND (e.seq >= ? ");
			params.add(boundarySeq);
			if (!named.isEmpty()) {
				sql.append("OR e.id IN (").append(placeholders(named.size())).append(") ");
				params.addAll(named);
			}
			sql.append(") ");
		}
		sql.append("ORDER BY e.seq");
		List<SessionEvent> rows = this.jdbcTemplate.query(sql.toString(), new SessionEventRowMapper(),
				params.toArray());
		return filter.apply(rows);
	}

	/**
	 * Runs the filter as one query: the per-event criteria as {@code WHERE} clauses and a
	 * last window as {@code ORDER BY seq DESC LIMIT n}, reversed back to log order and
	 * extended to the start of its turn ({@link #extendToTurnStart}) with one more query.
	 */
	private List<SessionEvent> findFiltered(String sessionId, EventFilter filter) {
		StringBuilder sql = new StringBuilder(SELECT_EVENTS_BASE);
		List<Object> params = new ArrayList<>();
		params.add(sessionId);
		appendCriteria(sql, params, filter, "e");
		if (filter.window() instanceof EventFilter.Window.Last last) {
			sql.append("ORDER BY e.seq DESC LIMIT ? ");
			params.add(last.n());
		}
		else {
			sql.append("ORDER BY e.seq ");
		}
		List<SessionEvent> result = this.jdbcTemplate.query(sql.toString(), new SessionEventRowMapper(),
				params.toArray());
		if (filter.window() instanceof EventFilter.Window.Last) {
			result = new ArrayList<>(result);
			Collections.reverse(result);
			result = extendToTurnStart(sessionId, filter, result);
		}
		return result;
	}

	/**
	 * Prepends the rest of the turn a {@code lastN} window landed in, so that the window
	 * starts at a {@linkplain SessionEvent#isTurnStart() turn start}. Runs one extra
	 * query only when it can matter: the window is full (fewer than N rows means the
	 * whole matched log is already in hand), its oldest event is not a turn start and the
	 * filter does not exclude {@code USER} events (no turn start could match). The query
	 * is bounded by one turn of the filtered log and uses the {@code (session_id, seq)}
	 * index; a subquery that finds no preceding turn start yields no rows, which is the
	 * plain-window fallback of {@link EventFilter#applyWindow(List)}.
	 * <p>
	 * The two reads are not one snapshot: a compaction committing in between can only
	 * shorten the extension of that one read, which the next turn corrects.
	 */
	private List<SessionEvent> extendToTurnStart(String sessionId, EventFilter filter, List<SessionEvent> window) {
		if (!(filter.window() instanceof EventFilter.Window.Last last) || window.size() < last.n()
				|| window.get(0).isTurnStart()
				|| (filter.messageTypes() != null && !filter.messageTypes().contains(MessageType.USER))
				|| (filter.eventTypes() != null && !filter.eventTypes().contains(EventType.MESSAGE))) {
			// A turn start is a USER message: a filter that admits none has no turn to
			// extend to
			return window;
		}
		StringBuilder sql = new StringBuilder(SELECT_EVENTS_BASE);
		List<Object> params = new ArrayList<>();
		params.add(sessionId);
		appendCriteria(sql, params, filter, "e");
		sql.append("AND e.seq < (SELECT w.seq FROM AI_SESSION_EVENT w WHERE w.id = ? AND w.session_id = ?) ");
		params.add(window.get(0).getId());
		params.add(sessionId);
		sql.append("AND e.seq >= (SELECT MAX(u.seq) FROM AI_SESSION_EVENT u WHERE u.session_id = ? ");
		params.add(sessionId);
		sql.append("AND u.event_type = ? AND u.message_type = ? ");
		params.add(EventType.MESSAGE.name());
		params.add(MessageType.USER.name());
		sql.append("AND u.seq < (SELECT w.seq FROM AI_SESSION_EVENT w WHERE w.id = ? AND w.session_id = ?) ");
		params.add(window.get(0).getId());
		params.add(sessionId);
		appendCriteria(sql, params, filter, "u");
		sql.append(") ORDER BY e.seq");
		List<SessionEvent> head = this.jdbcTemplate.query(sql.toString(), new SessionEventRowMapper(),
				params.toArray());
		if (head.isEmpty()) {
			return window;
		}
		List<SessionEvent> extended = new ArrayList<>(head.size() + window.size());
		extended.addAll(head);
		extended.addAll(window);
		return extended;
	}

	private void appendCriteria(StringBuilder sql, List<Object> params, EventFilter filter, String alias) {
		String col = alias + ".";
		if (filter.eventTypes() != null && !filter.eventTypes().isEmpty()) {
			sql.append("AND ")
				.append(col)
				.append("event_type IN (")
				.append(placeholders(filter.eventTypes().size()))
				.append(") ");
			filter.eventTypes().forEach(et -> params.add(et.name()));
		}
		if (filter.messageTypes() != null && !filter.messageTypes().isEmpty()) {
			// A NULL message_type (an event that is not a message) never matches.
			sql.append("AND ")
				.append(col)
				.append("message_type IN (")
				.append(placeholders(filter.messageTypes().size()))
				.append(") ");
			filter.messageTypes().forEach(mt -> params.add(mt.name()));
		}
		if (filter.excludeSynthetic()) {
			sql.append("AND ").append(col).append("synthetic = ? ");
			params.add(false);
		}

	}

	// -------------------------------------------------------------------------
	// Internal helpers
	// -------------------------------------------------------------------------

	private void insertEvent(SessionEvent event) {
		SessionEventCodec.EncodedEvent encoded = this.codec.encode(event);
		this.jdbcTemplate.update(INSERT_EVENT, event.getId(), event.getSessionId(), toUtc(event.getTimestamp()),
				encoded.type().name(), messageTypeName(encoded.messageType()), encoded.text(), encoded.payload(),
				event.isSynthetic(), this.codec.toJson(event.getMetadata()));
	}

	@Nullable private static String messageTypeName(@Nullable MessageType messageType) {
		return messageType != null ? messageType.name() : null;
	}

	/** Inserts the events in one JDBC batch. */
	private void batchInsertEvents(List<SessionEvent> events) {
		if (events.isEmpty()) {
			return;
		}
		this.jdbcTemplate.batchUpdate(INSERT_EVENT, events, events.size(), (ps, event) -> {
			SessionEventCodec.EncodedEvent encoded = this.codec.encode(event);
			ps.setString(1, event.getId());
			ps.setString(2, event.getSessionId());
			ps.setObject(3, toUtc(event.getTimestamp()));
			ps.setString(4, encoded.type().name());
			ps.setString(5, messageTypeName(encoded.messageType()));
			ps.setString(6, encoded.text());
			ps.setString(7, encoded.payload());
			ps.setBoolean(8, event.isSynthetic());
			ps.setString(9, this.codec.toJson(event.getMetadata()));
		});
	}

	/** {@code ?,?,…} for an {@code IN} list of {@code n} values. */
	private static String placeholders(int n) {
		return String.join(",", Collections.nCopies(n, "?"));
	}

	/**
	 * Timestamp columns carry no time zone ({@code TIMESTAMP} / {@code DATETIME}), so
	 * instants are always stored and read as UTC wall-clock time. Binding through
	 * {@link java.sql.Timestamp} would instead use the JVM default time zone, shifting
	 * values between application instances in different zones and across DST changes.
	 */
	@Nullable private static LocalDateTime toUtc(@Nullable Instant instant) {
		return instant != null ? LocalDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
	}

	@Nullable private static Instant fromUtc(ResultSet rs, String column) throws SQLException {
		LocalDateTime value = rs.getObject(column, LocalDateTime.class);
		return value != null ? value.toInstant(ZoneOffset.UTC) : null;
	}

	/** Returns a new {@link Builder}. */
	public static Builder builder() {
		return new Builder();
	}

	// -------------------------------------------------------------------------
	// Row mappers
	// -------------------------------------------------------------------------

	private class SessionRowMapper implements RowMapper<Session> {

		@Override
		public Session mapRow(ResultSet rs, int rowNum) throws SQLException {
			Map<String, Object> metadata = new HashMap<>(
					JdbcSessionRepository.this.codec.fromJsonMap(rs.getString("metadata")));
			// A NULL expires_at is a session that never expires and reads back as such
			return Session.builder()
				.id(rs.getString("id"))
				.createdAt(Objects.requireNonNull(fromUtc(rs, "created_at")))
				.expiresAt(fromUtc(rs, "expires_at"))
				.metadata(metadata)
				.build();
		}

	}

	private class SessionEventRowMapper implements RowMapper<SessionEvent> {

		@Override
		public SessionEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
			SessionEventCodec codec = JdbcSessionRepository.this.codec;
			String eventTypeName = rs.getString("event_type");
			EventType eventType = (eventTypeName != null) ? EventType.of(eventTypeName) : EventType.MESSAGE;
			String messageTypeName = rs.getString("message_type");
			MessageType messageType = (messageTypeName != null) ? MessageType.valueOf(messageTypeName) : null;
			SessionEvent.Builder builder = codec.decode(new SessionEventCodec.EncodedEvent(eventType, messageType,
					rs.getString("message_content"), rs.getString("payload")));

			// Merge the dedicated synthetic column back into the metadata map so that
			// SessionEvent.isSynthetic() returns the correct value.
			Map<String, Object> metadata = new HashMap<>(codec.fromJsonMap(rs.getString("metadata")));
			if (rs.getBoolean("synthetic")) {
				metadata.put(SessionEvent.METADATA_SYNTHETIC, true);
			}

			return builder.id(rs.getString("id"))
				.sessionId(rs.getString("session_id"))
				.timestamp(Objects.requireNonNull(fromUtc(rs, "timestamp")))
				.metadata(metadata)
				.build();
		}

	}

	// -------------------------------------------------------------------------
	// Builder
	// -------------------------------------------------------------------------

	/**
	 * Builds a {@link JdbcSessionRepository} on a {@link #dataSource(DataSource)
	 * DataSource} or a {@link #jdbcTemplate(JdbcTemplate) JdbcTemplate}; everything else
	 * has a default.
	 */
	public static final class Builder {

		@Nullable private DataSource dataSource;

		@Nullable private JdbcTemplate jdbcTemplate;

		@Nullable private JdbcSessionRepositoryDialect dialect;

		@Nullable private PlatformTransactionManager transactionManager;

		private JsonMapper jsonMapper = JsonMapper.builder().build();

		private Set<String> indexedAttributes = DEFAULT_INDEXED_ATTRIBUTES;

		private Builder() {
		}

		/**
		 * The metadata keys indexed in {@code AI_SESSION_INDEX}, so that
		 * {@link #findByMetadata} answers for them; the
		 * {@link #DEFAULT_INDEXED_ATTRIBUTES}, the principal, are always indexed.
		 */
		public Builder indexedAttributes(Set<String> indexedAttributes) {
			Assert.notNull(indexedAttributes, "indexedAttributes must not be null");
			Set<String> keys = new HashSet<>(indexedAttributes);
			keys.addAll(DEFAULT_INDEXED_ATTRIBUTES);
			this.indexedAttributes = keys;
			return this;
		}

		/** The data source; the dialect is detected from it unless set. */
		public Builder dataSource(DataSource dataSource) {
			this.dataSource = dataSource;
			return this;
		}

		/** A configured {@link JdbcTemplate}, instead of one on the data source. */
		public Builder jdbcTemplate(JdbcTemplate jdbcTemplate) {
			this.jdbcTemplate = jdbcTemplate;
			return this;
		}

		/**
		 * The SQL dialect; detected with
		 * {@link JdbcSessionRepositoryDialect#from(DataSource)} by default.
		 */
		public Builder dialect(JdbcSessionRepositoryDialect dialect) {
			this.dialect = dialect;
			return this;
		}

		/** The transaction manager; one on the data source by default. */
		public Builder transactionManager(PlatformTransactionManager transactionManager) {
			this.transactionManager = transactionManager;
			return this;
		}

		/**
		 * The mapper of the metadata and payload JSON; {@code
		 * JsonMapper.builder().build()} by default.
		 */
		public Builder jsonMapper(JsonMapper jsonMapper) {
			this.jsonMapper = jsonMapper;
			return this;
		}

		/** Builds the repository. */
		public JdbcSessionRepository build() {
			DataSource ds = resolveDataSource();
			JdbcTemplate jt = this.jdbcTemplate != null ? this.jdbcTemplate : new JdbcTemplate(ds);
			JdbcSessionRepositoryDialect d = this.dialect != null ? this.dialect
					: JdbcSessionRepositoryDialect.from(ds);
			PlatformTransactionManager txm = this.transactionManager != null ? this.transactionManager
					: new DataSourceTransactionManager(ds);
			return new JdbcSessionRepository(jt, d, txm, new SessionEventCodec(this.jsonMapper),
					this.indexedAttributes);
		}

		private DataSource resolveDataSource() {
			if (this.dataSource != null) {
				return this.dataSource;
			}
			if (this.jdbcTemplate != null && this.jdbcTemplate.getDataSource() != null) {
				return this.jdbcTemplate.getDataSource();
			}
			throw new IllegalArgumentException("A DataSource is required — set via dataSource() or jdbcTemplate()");
		}

	}

}
