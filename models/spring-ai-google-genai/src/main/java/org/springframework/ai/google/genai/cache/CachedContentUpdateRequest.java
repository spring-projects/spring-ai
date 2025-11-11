/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.google.genai.cache;

import java.time.Duration;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request for updating cached content in Google GenAI.
 *
 * @author Dan Dobrin
 * @since 1.1.0
 */
public final class CachedContentUpdateRequest {

	@JsonProperty("ttl")
	private final Duration ttl;

	@JsonProperty("expire_time")
	private final Instant expireTime;

	private CachedContentUpdateRequest(Builder builder) {
		this.ttl = builder.ttl;
		this.expireTime = builder.expireTime;
	}

	public Duration getTtl() {
		return this.ttl;
	}

	public Instant getExpireTime() {
		return this.expireTime;
	}

	@Override
	public String toString() {
		return "CachedContentUpdateRequest{" + "ttl=" + this.ttl + ", expireTime=" + this.expireTime + '}';
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private Duration ttl;

		private Instant expireTime;

		private Builder() {
		}

		public Builder ttl(Duration ttl) {
			this.ttl = ttl;
			return this;
		}

		public Builder expireTime(Instant expireTime) {
			this.expireTime = expireTime;
			return this;
		}

		public CachedContentUpdateRequest build() {
			if (this.ttl == null && this.expireTime == null) {
				throw new IllegalArgumentException("Either TTL or expire time must be set for update");
			}
			return new CachedContentUpdateRequest(this);
		}

	}

}
