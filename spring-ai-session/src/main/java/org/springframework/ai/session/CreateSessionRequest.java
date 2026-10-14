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
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Parameters for creating a new {@link Session}.
 *
 * <p>
 * Scope attributes such as {@link SessionAttributes#PRINCIPAL_NAME} go into the
 * {@link #metadata()}.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class CreateSessionRequest {

	@Nullable private final String id;

	@Nullable private final Duration timeToLive;

	private final Map<String, Object> metadata;

	private CreateSessionRequest(@Nullable String id, @Nullable Duration timeToLive, Map<String, Object> metadata) {
		this.id = id;
		this.timeToLive = timeToLive;
		this.metadata = Map.copyOf(metadata);
	}

	/**
	 * Returns the requested session ID, or {@code null} if the service should generate
	 * one.
	 */
	@Nullable public String id() {
		return this.id;
	}

	@Nullable public Duration timeToLive() {
		return this.timeToLive;
	}

	public Map<String, Object> metadata() {
		return this.metadata;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		@Nullable private String id;

		@Nullable private Duration timeToLive;

		private Map<String, Object> metadata = new HashMap<>();

		/**
		 * Sets an explicit session ID. If omitted, the service generates a UUID.
		 */
		public Builder id(@Nullable String id) {
			this.id = id;
			return this;
		}

		public Builder timeToLive(@Nullable Duration timeToLive) {
			this.timeToLive = timeToLive;
			return this;
		}

		public Builder metadata(Map<String, Object> metadata) {
			this.metadata.putAll(metadata);
			return this;
		}

		public Builder metadata(String key, Object value) {
			this.metadata.put(key, value);
			return this;
		}

		public CreateSessionRequest build() {
			return new CreateSessionRequest(this.id, this.timeToLive, this.metadata);
		}

	}

}
