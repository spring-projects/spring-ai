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
 * Interface for customizing MCP client components.
 * <p>
 * This interface allows for customization of MCP client components, such as clients or
 * transports, through Spring's customizer pattern. Implementations can modify the
 * component's configuration before it is used in the application.
 * <p>
 * Use {@code McpCustomizer<McpClient.SyncSpec>} for synchronous clients,
 * {@code McpCustomizer<McpClient.AsyncSpec>} for asynchronous clients, or
 * {@code McpCustomizer<HttpClientStreamableHttpTransport.Builder>} for transports.
 *
 * @param <T> the type of the MCP component to customize, e.g.
 * {@link io.modelcontextprotocol.client.McpClient.SyncSpec} or
 * {@link io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport.Builder}
 * @author Daniel Garnier-Moiroux
 * @since 2.0.0
 */
public interface McpClientCustomizer<T> {

	/**
	 * Customizes an MCP client component.
	 * <p>
	 * This method is called for each MCP component being created, allowing for
	 * component-specific customizations based on the component's name.
	 * @param name the name of the MCP component being customized
	 * @param component the component to customize
	 */
	void customize(String name, T component);

}
