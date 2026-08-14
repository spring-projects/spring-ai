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

import java.util.Set;

import org.springframework.ai.tool.toolsearch.ToolIndex;

/**
 * Strategy that decides which session tool indexes should be evicted.
 * <p>
 * {@link #onAccess(String)} is called at the start of every request. Any session IDs it
 * returns are cleared from the {@link ToolIndex}, after which {@link #onRemoved(String)}
 * is called so the strategy can drop its own internal tracking state.
 * <p>
 * Common strategies include: never evicting (indexes persist until explicitly removed),
 * always evicting (fresh re-index on every request), LRU eviction (cap on active session
 * count), TTL eviction (idle sessions expire after a configured duration), and composite
 * eviction (union of multiple strategies).
 *
 * @author Christian Tzolov
 * @since 2.0.0
 */
public interface ToolIndexEvictionStrategy {

	/**
	 * Called each time a session is accessed at the start of request processing.
	 * <p>
	 * Implementations should update their internal tracking state for {@code sessionId}
	 * and return the set of session IDs whose tool indexes must now be cleared. The
	 * returned set must never include the currently-accessed {@code sessionId}.
	 * @param sessionId the session being accessed
	 * @return session IDs to evict; never {@code null}, may be empty
	 */
	Set<String> onAccess(String sessionId);

	/**
	 * Called after a session's tool index has been cleared — either because this strategy
	 * returned it from {@link #onAccess}, or because of an explicit eviction request.
	 * <p>
	 * Implementations should remove any internal tracking state for the evicted session.
	 * @param sessionId the session that was evicted
	 */
	void onRemoved(String sessionId);

}
