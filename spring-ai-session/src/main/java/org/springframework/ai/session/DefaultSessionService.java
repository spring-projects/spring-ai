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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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

	/**
	 * How many times a compaction that a concurrent append overtook is rebased onto the
	 * new events and written again before it is given up.
	 */
	static final int MAX_REBASES = 2;

	/** The newest compaction boundary of a session. */
	private static final EventFilter NEWEST_BOUNDARY = EventFilter.builder()
		.eventTypes(Set.of(EventType.COMPACTION))
		.lastN(1)
		.build();

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
		checkSystemMessage(event);
		this.sessionRepository.appendEvent(event);
	}

	@Override
	public boolean appendEvents(String sessionId, List<SessionEvent> events, long expectedVersion) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		Assert.notEmpty(events, "events must not be null or empty");
		events.forEach(this::checkSystemMessage);
		return this.sessionRepository.appendEvents(sessionId, events, expectedVersion);
	}

	@Override
	public long getEventVersion(String sessionId) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		return this.sessionRepository.getEventVersion(sessionId);
	}

	private void checkSystemMessage(SessionEvent event) {
		Assert.notNull(event, "event must not be null");
		if (!this.allowSystemMessages && event.hasMessage() && event.getMessageType() == MessageType.SYSTEM) {
			throw new IllegalArgumentException("Storing a SystemMessage in session '" + event.getSessionId()
					+ "' is disabled. System prompts are configuration: supply them on every request "
					+ "(e.g. ChatClient defaultSystem/.system, or your own prompt builder) and keep per-session "
					+ "settings in Session.metadata. To store system messages anyway, use "
					+ "DefaultSessionService.builder().allowSystemMessages(true) or set "
					+ "spring.ai.session.allow-system-messages=true. See the \"System Messages\" reference page.");
		}
	}

	@Override
	public List<SessionEvent> getEvents(String sessionId, EventFilter filter) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		Assert.notNull(filter, "filter must not be null");
		return this.sessionRepository.findEvents(sessionId, filter);
	}

	@Override
	public CompactionResult compact(String sessionId, CompactionTrigger trigger, CompactionStrategy strategy) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		Assert.notNull(trigger, "trigger must not be null");
		Assert.notNull(strategy, "strategy must not be null");

		Session session = requireSession(sessionId);

		// The version is read before the boundary and the view, so the version passed to
		// the version-checked write is at most the one the view was read at, and the
		// boundary read says which view that was: a write that fails on the version
		// then tells an append apart from another compaction.
		long version = this.sessionRepository.getEventVersion(session.id());
		String boundary = newestBoundaryId(session.id());

		// The active view only: the events an earlier compaction covered are kept for
		// recall and are not summarized again.
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
		Set<String> seen = new HashSet<>();
		view.forEach(event -> seen.add(event.getId()));
		for (int rebases = 0;; rebases++) {
			if (this.sessionRepository.appendEvents(session.id(), result.toEvents(session.id()), version)) {
				return result;
			}
			// A concurrent writer changed the log. Another compaction's boundary makes
			// this result stale, its view is gone. An append does not: the summary still
			// stands in for what it covered, and the events appended since join the
			// preserved tail verbatim, so the result is rebased onto them and written
			// again, without a second summary. Bounded, so a busy session cannot hold
			// the compaction up for long.
			if (rebases == MAX_REBASES) {
				logger.debug("Compaction of session [{}] overtaken {} times; giving up until the next pass",
						session.id(), rebases + 1);
				return CompactionResult.unchanged();
			}
			version = this.sessionRepository.getEventVersion(session.id());
			if (!Objects.equals(boundary, newestBoundaryId(session.id()))) {
				return CompactionResult.unchanged();
			}
			List<SessionEvent> since = new ArrayList<>();
			for (SessionEvent event : this.sessionRepository.findEvents(session.id(), EventFilter.active())) {
				if (seen.add(event.getId())) {
					since.add(event);
				}
			}
			result = rebase(result, since);
		}
	}

	private @Nullable String newestBoundaryId(String sessionId) {
		List<SessionEvent> boundaries = this.sessionRepository.findEvents(sessionId, NEWEST_BOUNDARY);
		return boundaries.isEmpty() ? null : boundaries.get(boundaries.size() - 1).getId();
	}

	/**
	 * The result with the events appended since its view was read preserved after its
	 * tail. A stored system message among them is preserved like the rest, not pinned:
	 * the next compaction pins it and covers the one before.
	 */
	private static CompactionResult rebase(CompactionResult result, List<SessionEvent> since) {
		if (since.isEmpty()) {
			return result;
		}
		List<SessionEvent> preserved = new ArrayList<>(result.preservedEvents());
		preserved.addAll(since);
		return new CompactionResult(result.coveredEvents(), result.pinnedEvents(), preserved, result.summary(),
				result.tokensEstimatedSaved(), result.strategy());
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
