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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.advisor.tool.search.api.ToolIndexEvictionStrategy;
import org.springframework.util.Assert;

/**
 * A {@link ToolIndexEvictionStrategy} that delegates to multiple strategies and unions
 * their eviction decisions.
 * <p>
 * All delegates receive every {@link #onAccess} and {@link #onRemoved} call. A session is
 * evicted if <em>any</em> delegate requests its eviction. Typical usage is to combine LRU
 * capacity bounding with TTL-based idle cleanup.
 *
 * @author Christian Tzolov
 * @since 2.0.0
 */
public final class CompositeEvictionStrategy implements ToolIndexEvictionStrategy {

	private final List<ToolIndexEvictionStrategy> delegates;

	/**
	 * Creates a composite strategy from the given delegates.
	 * @param strategies one or more strategies to combine; must not be empty
	 */
	public CompositeEvictionStrategy(ToolIndexEvictionStrategy... strategies) {
		Assert.notEmpty(strategies, "at least one strategy is required");
		this.delegates = List.of(strategies);
	}

	@Override
	public Set<String> onAccess(String sessionId) {
		return this.delegates.stream().flatMap(s -> s.onAccess(sessionId).stream()).collect(Collectors.toSet());
	}

	@Override
	public void onRemoved(String sessionId) {
		this.delegates.forEach(s -> s.onRemoved(sessionId));
	}

}
