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

package org.springframework.ai.chat.client.advisor.tool.search.eviction;

import java.util.Set;

import org.springframework.ai.chat.client.advisor.tool.search.api.ToolIndexEvictionStrategy;

/**
 * A {@link ToolIndexEvictionStrategy} that clears a session's tool index before every
 * request, ensuring tools are always re-indexed on each conversation turn.
 * <p>
 * Use this strategy when the tool set may change between turns (e.g. dynamically
 * generated tool descriptions) and you want to guarantee freshness at the cost of
 * re-indexing on every request. For stable tool sets, {@link NeverEvictStrategy} is more
 * efficient.
 * <p>
 * <b>Contract note:</b> {@link #onAccess} intentionally returns the currently-accessed
 * session ID, which diverges from the general recommendation in
 * {@link ToolIndexEvictionStrategy#onAccess}. This is safe because the advisor's
 * {@code doEvict} + fingerprint-recheck sequence is idempotent: evicting the current
 * session removes its cached fingerprint, so the subsequent fingerprint comparison always
 * sees {@code null} and triggers a full re-index. When combined with other strategies in
 * a {@link CompositeEvictionStrategy}, the always-evict behaviour is preserved (the more
 * aggressive strategy wins).
 *
 * @author Christian Tzolov
 * @since 2.0.0
 * @see NeverEvictStrategy
 * @see LruEvictionStrategy
 * @see TtlEvictionStrategy
 */
public final class AlwaysEvictStrategy implements ToolIndexEvictionStrategy {

	/**
	 * Shared singleton instance.
	 */
	public static final AlwaysEvictStrategy INSTANCE = new AlwaysEvictStrategy();

	private AlwaysEvictStrategy() {
	}

	/**
	 * Returns a set containing {@code sessionId}, causing the advisor to clear the
	 * session's index and remove its cached fingerprint before the fingerprint-comparison
	 * step. The comparison then always sees a missing fingerprint and re-indexes the full
	 * tool set.
	 * @param sessionId the session being accessed
	 * @return a singleton set containing {@code sessionId}
	 */
	@Override
	public Set<String> onAccess(String sessionId) {
		return Set.of(sessionId);
	}

	@Override
	public void onRemoved(String sessionId) {
	}

}
