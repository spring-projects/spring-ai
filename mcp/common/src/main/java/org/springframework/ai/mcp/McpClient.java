/*
 * Copyright 2023-2024 the original author or authors.
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Qualifier annotation for injecting a specific MCP client by its connection name.
 *
 * <p>
 * This annotation is the recommended way to selectively bind MCP clients to specific
 * components, allowing different {@code ChatClient} instances to use different MCP
 * servers.
 *
 * <p>
 * Example usage:
 *
 * <pre>
 * {@code @Bean}
 * public ChatClient fileAgent(ChatClient.Builder builder,
 *         {@code @McpClient("filesystem")} McpSyncClient fsClient) {
 *     return builder
 *             .defaultTools(new SyncMcpToolCallbackProvider(List.of(fsClient)))
 *             .build();
 * }
 * </pre>
 *
 * <p>
 * The {@code value} corresponds to the connection name defined in the application
 * properties (e.g.,
 * {@code spring.ai.mcp.client.streamable-http.connections.filesystem.url}).
 *
 * <p>
 * Note: Standard {@link Qualifier @Qualifier("connectionName")} is also supported as a
 * fallback for users who prefer standard Spring annotations. However, using
 * {@code @McpClient} is recommended for better readability and future extensibility.
 *
 * @author Taewoong Kim
 * @see io.modelcontextprotocol.client.McpSyncClient
 * @see io.modelcontextprotocol.client.McpAsyncClient
 */
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Qualifier
public @interface McpClient {

	/**
	 * The connection name as defined in the application properties.
	 * <p>
	 * For example, if you have configured
	 * {@code spring.ai.mcp.client.streamable-http.connections.my-server.url=...} then the
	 * value should be {@code "my-server"}.
	 * @return the connection name
	 */
	String value();

}
