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

package org.springframework.ai.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;

import org.springframework.core.Ordered;

/**
 * Decides per request whether a server-side tool is advertised to the caller of
 * {@code tools/list}.
 * <p>
 * This is the server-side counterpart of the client-side {@link McpToolFilter}. A tool
 * hidden by a filter is omitted from the listing, which keeps context small and stops the
 * model from attempting calls it is not allowed to make.
 * <p>
 * Filters are applied in {@link Ordered} order and combined with AND semantics: a tool is
 * advertised only when every registered filter returns {@code true} for it. Registering
 * no filter advertises every tool.
 *
 * <h2>Hiding is not authorization</h2>
 *
 * A hidden tool is omitted from listings <em>only</em>. It stays reachable through
 * {@code tools/call} when a caller asks for it by name, so a filter must never be the
 * sole enforcement point for a permission. Authorization still belongs in the tool
 * handler itself, for example with Spring Security's {@code @PreAuthorize}.
 *
 * <h2>Per-caller identity</h2>
 *
 * The supplied {@link McpTransportContext} carries whatever the server's
 * {@code McpTransportContextExtractor} exposes for the request, typically HTTP headers or
 * a resolved principal. Transports that carry no per-request metadata, such as STDIO,
 * pass {@link McpTransportContext#EMPTY}, so implementations should treat a missing key
 * as "no caller identity" instead of failing.
 *
 * @author ZhangDT-sky
 * @since 2.1.0
 * @see McpToolFilter
 */
public interface McpServerToolFilter extends Ordered {

	/**
	 * Whether the given tool is visible to the caller of the current request.
	 * @param transportContext the transport context of the current request, never
	 * {@code null} but possibly {@link McpTransportContext#EMPTY}
	 * @param tool the candidate tool for the {@code tools/list} result
	 * @return {@code true} to advertise the tool, {@code false} to omit it from the
	 * listing
	 */
	boolean isVisible(McpTransportContext transportContext, McpSchema.Tool tool);

	/**
	 * Filters are AND-combined, so the order only determines the sequence in which they
	 * are consulted.
	 */
	@Override
	default int getOrder() {
		return 0;
	}

}
