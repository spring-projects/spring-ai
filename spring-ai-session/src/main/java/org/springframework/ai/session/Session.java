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
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Immutable metadata container for a single, continuous conversation between a user and
 * an agent. Holds only identity, lifecycle ({@link #expiresAt()}) and {@link #metadata()
 * metadata} — the event log is stored separately in {@link SessionRepository} and
 * retrieved on demand via {@link SessionService}.
 *
 * <p>
 * A session has no owner field. Who it belongs to is an attribute in its
 * {@link #metadata()}, under a well-known key such as
 * {@link SessionAttributes#PRINCIPAL_NAME}, put there by the application and looked up
 * through {@link SessionRepository#findByIndex}.
 *
 * <p>
 * Mutations return new instances; the original is never modified.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class Session {

	private final String id;

	private final Instant createdAt;

	@Nullable private final Instant expiresAt;

	private final Map<String, Object> metadata;

	private Session(String id, Instant createdAt, @Nullable Instant expiresAt, Map<String, Object> metadata) {
		this.id = id;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
		this.metadata = Map.copyOf(metadata);
	}

	/** Unique identifier for this session. */
	public String id() {
		return this.id;
	}

	/** When this session was created. */
	public Instant createdAt() {
		return this.createdAt;
	}

	/**
	 * When this session expires (TTL-based lifecycle). {@code null} means no expiry.
	 * Sessions created through {@link SessionService#create(CreateSessionRequest)} always
	 * have an expiry (the request's or the service's default time-to-live); {@code null}
	 * only occurs for sessions built directly with {@code expiresAt(null)}.
	 */
	@Nullable public Instant expiresAt() {
		return this.expiresAt;
	}

	/**
	 * Arbitrary metadata: the scope and agent attributes ({@link SessionAttributes}, read
	 * back with {@link SessionAttributes#agentName(Session)}), tags, and whatever the
	 * application adds.
	 */
	public Map<String, Object> metadata() {
		return this.metadata;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private String id = "";

		private Instant createdAt = Instant.now();

		@Nullable private Instant expiresAt = Instant.now().plus(Duration.ofDays(60));

		private Map<String, Object> metadata = new HashMap<>();

		public Builder id(String id) {
			this.id = id;
			return this;
		}

		public Builder createdAt(Instant createdAt) {
			this.createdAt = createdAt;
			return this;
		}

		/**
		 * Sets the expiry. Defaults to 60 days from now; pass {@code null} for a session
		 * that never expires.
		 */
		public Builder expiresAt(@Nullable Instant expiresAt) {
			this.expiresAt = expiresAt;
			return this;
		}

		/**
		 * Adds every entry of the map, keeping the ones already set; like the request
		 * builder.
		 */
		public Builder metadata(Map<String, Object> metadata) {
			this.metadata.putAll(metadata);
			return this;
		}

		/** Adds one metadata entry. */
		public Builder metadata(String key, Object value) {
			this.metadata.put(key, value);
			return this;
		}

		public Session build() {
			Assert.hasText(this.id, "id must not be null or empty");
			return new Session(this.id, this.createdAt, this.expiresAt, this.metadata);
		}

	}

}
