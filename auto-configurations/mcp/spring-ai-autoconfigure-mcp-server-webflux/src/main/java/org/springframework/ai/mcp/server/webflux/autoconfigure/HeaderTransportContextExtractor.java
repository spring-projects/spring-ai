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

package org.springframework.ai.mcp.server.webflux.autoconfigure;

import java.util.Collection;
import java.util.List;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpTransportContextExtractor;

import org.springframework.ai.mcp.modern.McpTransportContexts;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.server.ServerRequest;

/**
 * The default {@link McpTransportContextExtractor} of the auto-configured modern WebFlux
 * transport: stores the request headers named in
 * {@code spring.ai.mcp.server.modern.context-headers} under
 * {@link McpTransportContexts#HEADERS}, lower-cased and without the {@code Mcp-*}
 * protocol headers, so that repositories, visibility filters and tool handlers can read
 * them through {@link McpTransportContexts}. With no names configured the transport
 * context is empty, so credentials such as {@code Authorization} or {@code Cookie} never
 * reach a handler unless the application asks for them. Define a
 * {@code McpTransportContextExtractor<ServerRequest>} bean to replace it.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class HeaderTransportContextExtractor implements McpTransportContextExtractor<ServerRequest> {

	private final List<String> headerNames;

	/**
	 * Creates an extractor that keeps the given headers, matched case-insensitively.
	 * @param headerNames the names of the headers to store; empty stores none
	 */
	public HeaderTransportContextExtractor(Collection<String> headerNames) {
		Assert.notNull(headerNames, "headerNames must not be null");
		Assert.noNullElements(headerNames, "headerNames must not contain null");
		this.headerNames = List.copyOf(headerNames);
	}

	@Override
	public McpTransportContext extract(ServerRequest request) {
		return McpTransportContexts.fromHeaders(this.headerNames, request.headers().asHttpHeaders()::get);
	}

}
