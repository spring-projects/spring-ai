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

package org.springframework.ai.tool.toolsearch.eviction;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.util.Assert;

/**
 * A {@link ToolIndexEvictionStrategy} that evicts sessions whose last-access time is
 * older than a configured time-to-live (TTL).
 * <p>
 * Expiry is evaluated lazily on each {@link #onAccess} call: all tracked sessions (other
 * than the one currently being accessed) are checked against the TTL, and any that have
 * exceeded it are returned for eviction. No background thread is used.
 * <p>
 * The TTL resets on every access, so a session that continues to receive requests will
 * not be evicted as long as the gap between consecutive requests stays below the TTL.
 *
 * @author Christian Tzolov
 * @since 2.0.0
 */
public final class TtlEvictionStrategy implements ToolIndexEvictionStrategy {

	private final Duration ttl;

	private final ConcurrentHashMap<String, Instant> lastAccess = new ConcurrentHashMap<>();

	/**
	 * Creates a new TtlEvictionStrategy.
	 * @param ttl maximum idle time before a session's index is evicted; must be positive
	 */
	public TtlEvictionStrategy(Duration ttl) {
		Assert.notNull(ttl, "ttl must not be null");
		Assert.isTrue(!ttl.isNegative() && !ttl.isZero(), "ttl must be positive");
		this.ttl = ttl;
	}

	@Override
	public Set<String> onAccess(String sessionId) {
		Instant now = Instant.now();
		this.lastAccess.put(sessionId, now);

		Set<String> expired = new HashSet<>();
		this.lastAccess.forEach((id, ts) -> {
			if (!id.equals(sessionId) && Duration.between(ts, now).compareTo(this.ttl) >= 0) {
				expired.add(id);
			}
		});
		expired.forEach(this.lastAccess::remove);
		return expired;
	}

	@Override
	public void onRemoved(String sessionId) {
		this.lastAccess.remove(sessionId);
	}

}
