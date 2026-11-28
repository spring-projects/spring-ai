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

package org.springframework.ai.mcp.modern.client;

import io.modelcontextprotocol.modern.client.SubscriptionEvent;

/**
 * Hears about the changes that modern MCP servers report on their listen streams.
 * {@link SyncModernMcpChangeSubscriber} keeps the streams open and calls the listeners.
 * <p>
 * The methods are called on a thread that may block, one call at a time for each
 * connection, so a listener may call the server, for example to list its tools again.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
@FunctionalInterface
public interface SyncModernMcpChangeListener {

	/**
	 * The server of a connection reported a change.
	 * @param connectionInfo the connection whose server changed
	 * @param event what changed: a {@link SubscriptionEvent.ToolsListChanged}, a
	 * {@link SubscriptionEvent.PromptsListChanged}, a
	 * {@link SubscriptionEvent.ResourcesListChanged} or a
	 * {@link SubscriptionEvent.ResourceUpdated}
	 */
	void changed(McpModernConnectionInfo connectionInfo, SubscriptionEvent event);

	/**
	 * Changes of the server may have gone unreported: a listen stream was established, so
	 * that what happened before it is not known, or an established stream ended. Whatever
	 * is kept of the server's tools, prompts or resources should be dropped or listed
	 * again. It is not called for a stream that could not be established, which reports
	 * nothing but also changes nothing about what is kept.
	 * @param connectionInfo the connection whose server may have changed
	 */
	default void changesMayBeMissed(McpModernConnectionInfo connectionInfo) {
	}

	/**
	 * A listen stream to the server of a connection was established, which shows that the
	 * server can be reached. It is called before {@link #changesMayBeMissed}.
	 * @param connectionInfo the connection whose server accepted the stream
	 */
	default void streamEstablished(McpModernConnectionInfo connectionInfo) {
	}

}
