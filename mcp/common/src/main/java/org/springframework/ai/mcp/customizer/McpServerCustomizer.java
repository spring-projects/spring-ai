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

package org.springframework.ai.mcp.customizer;

/**
 * Callback interface that can be implemented by beans wishing to customize an
 * auto-configured MCP server component, such as the server or its transport, before it is
 * built.
 * <p>
 * The type parameter selects which builder the customizer applies to. Use for example
 * {@code McpServerCustomizer<io.modelcontextprotocol.modern.server.McpServer.Builder>}
 * for the 2026-07-28 ("modern") server, or
 * {@code McpServerCustomizer<WebMvcStreamableServerTransportProvider.Builder>} for the
 * WebMVC Streamable HTTP transport. Customizers are applied in
 * {@link org.springframework.core.Ordered order}.
 *
 * @param <B> the type of the builder to customize, e.g.
 * {@link io.modelcontextprotocol.modern.server.McpServer.Builder}
 * @author Daniel Garnier-Moiroux
 * @since 2.1.0
 * @see McpClientCustomizer
 */
@FunctionalInterface
public interface McpServerCustomizer<B> {

	/**
	 * Customize the given builder.
	 * @param builder the builder to customize
	 */
	void customize(B builder);

}
