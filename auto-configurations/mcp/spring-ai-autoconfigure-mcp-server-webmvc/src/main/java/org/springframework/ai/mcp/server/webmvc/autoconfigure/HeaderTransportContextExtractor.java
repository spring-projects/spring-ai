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

package org.springframework.ai.mcp.server.webmvc.autoconfigure;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpTransportContextExtractor;

import org.springframework.ai.mcp.modern.McpTransportContexts;
import org.springframework.web.servlet.function.ServerRequest;

/**
 * The default {@link McpTransportContextExtractor} of the auto-configured modern WebMvc
 * transport: stores the request headers under {@link McpTransportContexts#HEADERS},
 * lower-cased and without the {@code Mcp-*} protocol headers, so that repositories,
 * visibility filters and tool handlers can read them through
 * {@link McpTransportContexts}. Define a
 * {@code McpTransportContextExtractor<ServerRequest>} bean to replace it.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
public final class HeaderTransportContextExtractor implements McpTransportContextExtractor<ServerRequest> {

	@Override
	public McpTransportContext extract(ServerRequest request) {
		return McpTransportContexts.fromHeaders(request.headers().asHttpHeaders()::forEach);
	}

}
