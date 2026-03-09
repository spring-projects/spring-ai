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

package org.springframework.ai.mcp.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for methods that handle resource list change notifications from MCP servers.
 * This annotation is applicable only for MCP clients.
 *
 * <p>
 * Methods annotated with this annotation are used to listen for notifications when the
 * list of available resources changes on an MCP server. According to the MCP
 * specification, servers that declare the {@code listChanged} capability will send
 * notifications when their resource list is modified.
 *
 * <p>
 * The annotated method must have a void return type for synchronous consumers, or can
 * return {@code Mono<Void>} for asynchronous consumers. The method should accept a single
 * parameter of type {@code List<McpSchema.Resource>} that represents the updated list of
 * resources after the change notification.
 *
 * <p>
 * Example usage: <pre>{@code
 * &#64;McpResourceListChanged(clients = "test-client")
 * public void onResourceListChanged(List<McpSchema.Resource> updatedResources) {
 *     // Handle resource list change notification with the updated resources
 *     logger.info("Resource list updated, now contains {} resources", updatedResources.size());
 *     // Process the updated resource list
 * }
 *
 * &#64;McpResourceListChanged(clients = "test-client")
 * public Mono<Void> onResourceListChangedAsync(List<McpSchema.Resource> updatedResources) {
 *     // Handle resource list change notification asynchronously
 *     return processUpdatedResources(updatedResources);
 * }
 * }</pre>
 *
 * @author Christian Tzolov
 * @see <a href=
 * "https://modelcontextprotocol.io/specification/2025-06-18/server/resources#list-changed-notification">MCP
 * Resource List Changed Notification</a>
 */
@Target({ ElementType.METHOD, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface McpResourceListChanged {

	/**
	 * Used as connection or client identifier to select the MCP clients that the resource
	 * change listener is associated with.
	 * @return the client identifier, or empty string to listen to all clients
	 */
	String[] clients();

}
