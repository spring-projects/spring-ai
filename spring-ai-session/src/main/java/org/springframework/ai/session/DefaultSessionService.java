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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.session.compaction.CompactionRequest;
import org.springframework.ai.session.compaction.CompactionResult;
import org.springframework.ai.session.compaction.CompactionStrategy;
import org.springframework.ai.session.compaction.CompactionTrigger;
import org.springframework.util.Assert;

/**
 * The {@link SessionService} on a {@link SessionRepository}.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class DefaultSessionService implements SessionService {

	private static final Logger logger = LoggerFactory.getLogger(DefaultSessionService.class);

	private final Duration defaultTimeToLive;

	private final SessionRepository sessionRepository;

	private final boolean allowSystemMessages;

	private DefaultSessionService(SessionRepository sessionRepository, Duration defaultTimeToLive,
			boolean allowSystemMessages) {
		Assert.notNull(sessionRepository, "sessionRepository must not be null");
		Assert.notNull(defaultTimeToLive, "defaultTimeToLive must not be null");
		this.sessionRepository = sessionRepository;
		this.defaultTimeToLive = defaultTimeToLive;
		this.allowSystemMessages = allowSystemMessages;
	}

	@Override
	public Session create(CreateSessionRequest request) {
		Assert.notNull(request, "request must not be null");

		Instant now = Instant.now();
		Instant expiresAt = (request.timeToLive() != null) ? now.plus(request.timeToLive())
				: now.plus(this.defaultTimeToLive);

		String sessionId = (request.id() != null && !request.id().isBlank()) ? request.id()
				: UUID.randomUUID().toString();

		Session session = Session.builder()
			.id(sessionId)
			.createdAt(now)
			.expiresAt(expiresAt)
			.metadata(request.metadata())
			.build();

		// Insert-only and atomic: never silently overwrite an existing session (an upsert
		// would reassign its owner and TTL while keeping its event log), even when two
		// callers create the same id concurrently.
		if (!this.sessionRepository.saveIfAbsent(session)) {
			throw new SessionAlreadyExistsException(sessionId);
		}
		return session;
	}

	@Override
	@Nullable public Session findById(String sessionId) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");

		return this.sessionRepository.findById(sessionId);
	}

	@Override
	public List<Session> findByMetadata(String name, String value) {
		Assert.hasText(name, "name must not be null or empty");
		Assert.notNull(value, "value must not be null");
		return this.sessionRepository.findByMetadata(name, value);
	}

	private Session requireSession(String sessionId) {
		Session session = this.sessionRepository.findById(sessionId);
		if (session == null) {
			throw new IllegalArgumentException("Session not found: " + sessionId);
		}
		return session;
	}

	@Override
	public void delete(String sessionId) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		this.sessionRepository.delete(sessionId);
	}

	@Override
	public int deleteExpiredSessions(Instant before) {
		Assert.notNull(before, "before must not be null");
		return this.sessionRepository.deleteExpiredSessions(before);
	}

	@Override
	public void appendEvent(SessionEvent event) {
		Assert.notNull(event, "event must not be null");
		if (!this.allowSystemMessages && event.hasMessage() && event.getMessageType() == MessageType.SYSTEM) {
			throw new IllegalArgumentException("Storing a SystemMessage in session '" + event.getSessionId()
					+ "' is disabled. System prompts are configuration: supply them on every request "
					+ "(e.g. ChatClient defaultSystem/.system, or your own prompt builder) and keep per-session "
					+ "settings in Session.metadata. To store system messages anyway, use "
					+ "DefaultSessionService.builder().allowSystemMessages(true) or set "
					+ "spring.ai.session.allow-system-messages=true. See the \"System Messages\" reference page.");
		}
		append(event);
	}

	@Override
	public List<SessionEvent> getEvents(String sessionId, EventFilter filter) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		Assert.notNull(filter, "filter must not be null");
		return this.sessionRepository.findEvents(sessionId, filter);
	}

	private void append(SessionEvent event) {
		this.sessionRepository.appendEvent(event);
	}

	@Override
	public CompactionResult compact(String sessionId, CompactionTrigger trigger, CompactionStrategy strategy) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		Assert.notNull(trigger, "trigger must not be null");
		Assert.notNull(strategy, "strategy must not be null");

		Session session = requireSession(sessionId);

		// Read version BEFORE events so the version we pass to the CAS write is
		// guaranteed to be ≤ the version of the events we subsequently read. If another
		// writer (append or compaction) changes the log between our read and our write,
		// the CAS will detect the version mismatch and return false — we skip silently,
		// as the concurrent writer already handled the session.
		long version = this.sessionRepository.getEventVersion(session.id());

		// Operate on the active view only — the events an earlier compaction covered are
		// retained for Recall Storage and must not be re-processed (or re-summarized).
		List<SessionEvent> view = this.sessionRepository.findEvents(session.id(), EventFilter.active());

		CompactionRequest request = CompactionRequest.of(session, view);

		if (!trigger.shouldCompact(request)) {
			return CompactionResult.unchanged();
		}

		CompactionResult result = strategy.compact(request);
		if (result.isUnchanged()) {
			return result;
		}

		// Record the decision as an appended boundary followed by its summary events. The
		// log is never changed: the boundary names, by id, what the view is from now on.
		List<SessionEvent> appended = result.toEvents(session.id());
		if (!this.sessionRepository.appendEvents(session.id(), appended, version)) {
			// CAS rejected — a concurrent writer already changed the log; skip silently.
			return CompactionResult.unchanged();
		}
		return result;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		@Nullable private SessionRepository sessionRepository;

		private Duration defaultTimeToLive = Duration.ofDays(60);

		private boolean allowSystemMessages = false;

		/** The store; required. */
		public Builder sessionRepository(SessionRepository sessionRepository) {
			Assert.notNull(sessionRepository, "sessionRepository must not be null");
			this.sessionRepository = sessionRepository;
			return this;
		}

		/**
		 * Whether {@link #appendEvent(SessionEvent)} may store a
		 * {@link org.springframework.ai.chat.messages.SystemMessage}; {@code false} by
		 * default. A system prompt is configuration, best supplied on every request, so
		 * storing one is rejected with an {@link IllegalArgumentException} that says how
		 * to enable it. When enabled, the latest stored system message is the session's
		 * system prompt, which compaction pins; see
		 * {@link org.springframework.ai.session.compaction.WindowCompactionStrategy}.
		 */
		public Builder allowSystemMessages(boolean allowSystemMessages) {
			this.allowSystemMessages = allowSystemMessages;
			return this;
		}

		/** The time to live of a session created without one; 60 days by default. */
		public Builder defaultTimeToLive(Duration defaultTimeToLive) {
			Assert.notNull(defaultTimeToLive, "defaultTimeToLive must not be null");
			this.defaultTimeToLive = defaultTimeToLive;
			return this;
		}

		/**
		 * Builds the service.
		 * @throws IllegalStateException if no repository was set
		 */
		public DefaultSessionService build() {
			if (this.sessionRepository == null) {
				throw new IllegalStateException("sessionRepository must be set");
			}
			return new DefaultSessionService(this.sessionRepository, this.defaultTimeToLive, this.allowSystemMessages);
		}

	}

}
