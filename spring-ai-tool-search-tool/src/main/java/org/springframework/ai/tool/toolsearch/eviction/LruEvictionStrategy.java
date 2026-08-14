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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.util.Assert;

/**
 * A {@link ToolIndexEvictionStrategy} that evicts the least-recently-used session when
 * the number of concurrently active sessions exceeds a configured maximum.
 * <p>
 * Access order is updated on every {@link #onAccess} call, so a session that receives a
 * new request is moved to the most-recently-used position and will not be a candidate for
 * eviction until all other sessions have been accessed.
 * <p>
 * This strategy is suitable for remote vector databases and API-based embedding models,
 * where the number of sessions whose embeddings are retained in the store must be
 * bounded.
 *
 * @author Christian Tzolov
 * @since 2.0.0
 */
public final class LruEvictionStrategy implements ToolIndexEvictionStrategy {

	private final int maxSessions;

	// access-order LinkedHashMap; iteration order = LRU → MRU
	private final Map<String, Boolean> accessOrder;

	/**
	 * Creates a new LruEvictionStrategy.
	 * @param maxSessions maximum number of sessions whose indexes are retained; must be
	 * positive
	 */
	public LruEvictionStrategy(int maxSessions) {
		Assert.isTrue(maxSessions > 0, "maxSessions must be positive");
		this.maxSessions = maxSessions;
		this.accessOrder = Collections.synchronizedMap(new LinkedHashMap<>(maxSessions + 1, 0.75f, true));
	}

	@Override
	public Set<String> onAccess(String sessionId) {
		synchronized (this.accessOrder) {
			this.accessOrder.put(sessionId, Boolean.TRUE);
			if (this.accessOrder.size() <= this.maxSessions) {
				return Set.of();
			}
			// First entry in iteration order is the LRU one.
			String lru = this.accessOrder.entrySet().iterator().next().getKey();
			this.accessOrder.remove(lru);
			return Set.of(lru);
		}
	}

	@Override
	public void onRemoved(String sessionId) {
		synchronized (this.accessOrder) {
			this.accessOrder.remove(sessionId);
		}
	}

}
