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

package org.springframework.ai.mcp.annotation.spring;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Qualifier annotation for injecting an individual MCP client by its connection name.
 *
 * <p>
 * Spring AI auto-configuration exposes individual client beans for named connections
 * declared through MCP client configuration properties. User-defined individual MCP
 * client beans can also opt in by annotating their bean method or component class.
 *
 * <p>
 * This annotation selects Spring bean candidates. It does not select objects that only
 * exist as elements of an aggregate collection bean.
 *
 * <p>
 * Example usage:
 *
 * <pre>
 * {@code @Bean}
 * public ChatClient fileAgent(ChatClient.Builder builder,
 *         {@code @McpClient("filesystem")} McpSyncClient filesystemClient) {
 *     return builder
 *             .defaultTools(SyncMcpToolCallbackProvider.builder()
 *                     .mcpClients(filesystemClient)
 *                     .build())
 *             .build();
 * }
 * </pre>
 *
 * @author Taewoong Kim
 * @see io.modelcontextprotocol.client.McpSyncClient
 * @see io.modelcontextprotocol.client.McpAsyncClient
 * @since 2.0.1
 */
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Qualifier
public @interface McpClient {

	/**
	 * The logical MCP connection name.
	 * @return the connection name
	 */
	String value();

}
