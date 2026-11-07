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

package org.springframework.ai.mcp.server.common.autoconfigure;

import java.util.List;

import io.modelcontextprotocol.server.McpAsyncListFilter;
import io.modelcontextprotocol.server.McpSyncListFilter;
import io.modelcontextprotocol.spec.McpSchema;

import org.springframework.ai.mcp.McpServerToolFilter;
import org.springframework.util.Assert;

/**
 * Adapts the ordered {@link McpServerToolFilter} beans to the SDK list filters accepted
 * by {@code McpServer} specifications.
 *
 * @author ZhangDT-sky
 * @since 2.1.0
 */
final class McpServerToolFilterAdapter {

	private McpServerToolFilterAdapter() {
	}

	/**
	 * Combine the given filters into a single AND-combined synchronous filter.
	 * @param filters the filters to combine, in {@link org.springframework.core.Ordered}
	 * order
	 * @return the combined filter
	 */
	static McpSyncListFilter<McpSchema.Tool> toSyncFilter(List<McpServerToolFilter> filters) {
		Assert.notNull(filters, "filters must not be null");
		List<McpServerToolFilter> snapshot = List.copyOf(filters);
		return (transportContext, tool) -> {
			for (McpServerToolFilter filter : snapshot) {
				if (!filter.isVisible(transportContext, tool)) {
					return false;
				}
			}
			return true;
		};
	}

	/**
	 * Combine the given filters into a single AND-combined asynchronous filter. The
	 * filters are offloaded off the reactor event loop because implementations may
	 * perform blocking work, such as a security decision.
	 * @param filters the filters to combine, in {@link org.springframework.core.Ordered}
	 * order
	 * @return the combined filter
	 */
	static McpAsyncListFilter<McpSchema.Tool> toAsyncFilter(List<McpServerToolFilter> filters) {
		return McpAsyncListFilter.fromSync(toSyncFilter(filters), false);
	}

}
