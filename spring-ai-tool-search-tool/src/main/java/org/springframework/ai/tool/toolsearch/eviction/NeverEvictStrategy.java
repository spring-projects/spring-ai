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

/**
 * A {@link ToolIndexEvictionStrategy} that never evicts sessions automatically.
 * <p>
 * Tool indexes persist across requests for the lifetime of the
 * {@code ToolSearchToolCallingAdvisor} bean, or until explicitly released via
 * {@code ToolSearchToolCallingAdvisor#evictSession(String)}.
 * <p>
 * <b>Memory warning:</b> this strategy accumulates one index entry per distinct session
 * ID and never frees them. In a service that handles many short-lived conversations (e.g.
 * HTTP sessions, anonymous users) the index map will grow without bound, eventually
 * exhausting heap (Lucene {@code ByteBuffersDirectory} instances) or remote vector-store
 * quota. For production deployments use {@link LruEvictionStrategy} to cap the number of
 * concurrently retained indexes, {@link TtlEvictionStrategy} to expire idle sessions, or
 * a {@link CompositeEvictionStrategy} combining both.
 * <p>
 * This strategy is appropriate only when the set of active sessions is small and stable
 * (e.g. a single-user tool or a bounded pool of long-running agents), or when the
 * application manages session lifecycle explicitly via
 * {@code ToolSearchToolCallingAdvisor#evictSession(String)}.
 *
 * @author Christian Tzolov
 * @since 2.0.0
 */
public final class NeverEvictStrategy implements ToolIndexEvictionStrategy {

	/**
	 * Shared singleton instance.
	 */
	public static final NeverEvictStrategy INSTANCE = new NeverEvictStrategy();

	private NeverEvictStrategy() {
	}

	@Override
	public Set<String> onAccess(String sessionId) {
		return Set.of();
	}

	@Override
	public void onRemoved(String sessionId) {
	}

}
