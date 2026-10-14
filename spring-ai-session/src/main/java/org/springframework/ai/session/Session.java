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
 * One continuous conversation between a user and an agent: its identity, its expiry and
 * its {@link #metadata() metadata}. An immutable value; the events are stored separately
 * in the {@link SessionRepository} and read through the {@link SessionService}.
 * <p>
 * A session has no owner field. Who it belongs to is an attribute of its metadata, under
 * a well-known key such as {@link SessionAttributes#PRINCIPAL_NAME}, set by the
 * application and looked up with {@link SessionRepository#findByIndex}.
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

	/** The session's identifier. */
	public String id() {
		return this.id;
	}

	/** When the session was created. */
	public Instant createdAt() {
		return this.createdAt;
	}

	/**
	 * When the session expires, or {@code null} for never. A session the service creates
	 * always has an expiry, the request's time to live or the service's default; {@code
	 * null} only for a session built directly with {@code expiresAt(null)}.
	 */
	@Nullable public Instant expiresAt() {
		return this.expiresAt;
	}

	/**
	 * The metadata: the owner and agent attributes ({@link SessionAttributes}), tags, and
	 * whatever the application adds.
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

		/** Adds every entry of the map, keeping the ones already set. */
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
