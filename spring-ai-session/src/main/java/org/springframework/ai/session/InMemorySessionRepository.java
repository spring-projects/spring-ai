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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Thread-safe, in-memory implementation of {@link SessionRepository}. Suitable for
 * development and testing. Not suitable for production use as state is lost on
 * application restart and not shared across instances.
 *
 * <p>
 * Session metadata and event log are stored together in a private {@code SessionData}
 * record. All mutations are performed atomically via {@link ConcurrentHashMap#compute}.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class InMemorySessionRepository implements SessionRepository {

	private final Map<String, SessionData> store = new ConcurrentHashMap<>();

	private InMemorySessionRepository() {
	}

	/**
	 * Returns a new {@link Builder} for {@code InMemorySessionRepository}.
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	@Override
	public Session save(Session session) {
		Assert.notNull(session, "session must not be null");
		SessionData saved = this.store.compute(session.id(), (id, existing) -> {
			if (existing == null) {
				return new SessionData(session, List.of(), 0L);
			}
			// Update: keep the original creation time, like the JDBC upsert does.
			Session updated = Session.builder()
				.id(session.id())
				.createdAt(existing.session().createdAt())
				.expiresAt(session.expiresAt())
				.metadata(session.metadata())
				.build();
			return new SessionData(updated, existing.events(), existing.version());
		});
		return saved.session();
	}

	@Override
	public boolean saveIfAbsent(Session session) {
		Assert.notNull(session, "session must not be null");
		return this.store.putIfAbsent(session.id(), new SessionData(session, List.of(), 0L)) == null;
	}

	@Override
	public @Nullable Session findById(String sessionId) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		SessionData data = this.store.get(sessionId);
		return (data != null) ? data.session() : null;
	}

	@Override
	public List<Session> findByIndex(String name, String value) {
		Assert.hasText(name, "name must not be null or empty");
		Assert.notNull(value, "value must not be null");
		return this.store.values()
			.stream()
			.map(SessionData::session)
			.filter(s -> s.metadata().containsKey(name) && value.equals(String.valueOf(s.metadata().get(name))))
			.sorted(Comparator.comparing(Session::createdAt).thenComparing(Session::id))
			.toList();
	}

	@Override
	public int deleteExpiredSessions(Instant before) {
		Assert.notNull(before, "before must not be null");
		int[] deleted = { 0 };
		for (String sessionId : this.store.keySet()) {
			// Atomic per session: a concurrent save() that extends the TTL wins
			this.store.computeIfPresent(sessionId, (id, data) -> {
				if (isExpired(data, before)) {
					deleted[0]++;
					return null;
				}
				return data;
			});
		}
		return deleted[0];
	}

	private static boolean isExpired(SessionData data, Instant before) {
		Instant expiresAt = data.session().expiresAt();
		return expiresAt != null && expiresAt.isBefore(before);
	}

	@Override
	public void delete(String sessionId) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		this.store.remove(sessionId);
	}

	@Override
	public void appendEvent(SessionEvent event) {
		Assert.notNull(event, "event must not be null");
		String sessionId = event.getSessionId();
		this.store.compute(sessionId, (id, existing) -> {
			if (existing == null) {
				throw new IllegalArgumentException("Session not found: " + sessionId);
			}
			boolean alreadyAppended = existing.events().stream().anyMatch(e -> e.getId().equals(event.getId()));
			if (alreadyAppended) {
				// Idempotent replay: an event with this id was already committed, e.g. a
				// retried append after a crash. No-op -- do not duplicate the event or
				// increment the version.
				return existing;
			}
			List<SessionEvent> newEvents = new ArrayList<>(existing.events());
			newEvents.add(event);
			return existing.withEvents(newEvents);
		});
	}

	@Override
	public boolean appendEvents(String sessionId, List<SessionEvent> events, long expectedVersion) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		Assert.notEmpty(events, "events must not be empty");
		boolean[] success = { false };
		this.store.compute(sessionId, (id, existing) -> {
			if (existing == null) {
				throw new IllegalArgumentException("Session not found: " + sessionId);
			}
			if (existing.version() != expectedVersion) {
				return existing;
			}
			// Validate everything before anything changes
			Set<String> ids = new HashSet<>();
			existing.events().forEach(e -> ids.add(e.getId()));
			for (SessionEvent event : events) {
				Assert.isTrue(sessionId.equals(event.getSessionId()),
						() -> "Event '" + event.getId() + "' belongs to session '" + event.getSessionId() + "'");
				Assert.isTrue(ids.add(event.getId()),
						() -> "Event id '" + event.getId() + "' is already in the log of session " + sessionId);
			}
			List<SessionEvent> newEvents = new ArrayList<>(existing.events());
			newEvents.addAll(events);
			success[0] = true;
			return existing.withEvents(newEvents);
		});
		return success[0];
	}

	@Override
	public long getEventVersion(String sessionId) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		SessionData data = this.store.get(sessionId);
		return (data != null) ? data.version() : 0L;
	}

	@Override
	public List<SessionEvent> findEvents(String sessionId, EventFilter filter) {
		Assert.hasText(sessionId, "sessionId must not be null or empty");
		Assert.notNull(filter, "filter must not be null");

		SessionData data = this.store.get(sessionId);
		return (data != null) ? filter.apply(data.events()) : List.of();
	}

	private record SessionData(Session session, List<SessionEvent> events, long version) {

		SessionData withEvents(List<SessionEvent> newEvents) {
			return new SessionData(this.session, newEvents, this.version + 1);
		}

	}

	/**
	 * Builder for {@link InMemorySessionRepository}.
	 */
	public static final class Builder {

		private Builder() {
		}

		/**
		 * Builds the {@link InMemorySessionRepository} instance.
		 * @return a new {@code InMemorySessionRepository}
		 */
		public InMemorySessionRepository build() {
			return new InMemorySessionRepository();
		}

	}

}
