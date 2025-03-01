/*
 * Copyright 2025-2025 the original author or authors.
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

import io.modelcontextprotocol.client.McpClient;

/**
 * Interface for customizing asynchronous MCP client configurations.
 * <p>
 * This interface allows for customization of MCP client behavior through Spring's
 * customizer pattern. Implementations can modify the client's configuration before it is
 * used in the application.
 * <p>
 *
 * @author Christian Tzolov
 * @since 1.0.0
 * @see io.modelcontextprotocol.client.McpClient.AsyncSpec
 */
public interface McpAsyncClientCustomizer {

	/**
	 * Customizes an asynchronous MCP client configuration.
	 * <p>
	 * This method is called for each async MCP client being created, allowing for
	 * client-specific customizations based on the client's name and specification.
	 * @param name the name of the MCP client being customized
	 * @param spec the async specification to customize
	 */
	void customize(String name, McpClient.AsyncSpec spec);

}
