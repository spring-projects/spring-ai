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
import reactor.core.publisher.Mono;

/**
 * Hears about the changes that modern MCP servers report on their listen streams, with
 * the asynchronous client type. {@link AsyncModernMcpChangeSubscriber} keeps the streams
 * open and calls the listeners.
 * <p>
 * A method returns a {@code Mono} that completes when the listener has dealt with what it
 * was told. The listeners are told one after the other, in their order: the next one is
 * told when the {@code Mono} of the previous one has completed. What the server of a
 * connection reports is told in the order it was reported, and the next report is told
 * when all listeners are done with the previous one. A listener must not block, and it
 * may call the server, for example to list its tools again. A {@code Mono} that never
 * completes holds up the later reports of its connection, so a call that may hang needs a
 * timeout.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
@FunctionalInterface
public interface AsyncModernMcpChangeListener {

	/**
	 * The server of a connection reported a change.
	 * @param connectionInfo the connection whose server changed
	 * @param event what changed: a {@link SubscriptionEvent.ToolsListChanged}, a
	 * {@link SubscriptionEvent.PromptsListChanged}, a
	 * {@link SubscriptionEvent.ResourcesListChanged} or a
	 * {@link SubscriptionEvent.ResourceUpdated}
	 * @return completes when the listener has dealt with the change
	 */
	Mono<Void> changed(McpModernConnectionInfo connectionInfo, SubscriptionEvent event);

	/**
	 * Changes of the server may have gone unreported: a listen stream was established, so
	 * that what happened before it is not known, or an established stream ended. Whatever
	 * is kept of the server's tools, prompts or resources should be dropped or listed
	 * again. It is not called for a stream that could not be established, which reports
	 * nothing but also changes nothing about what is kept.
	 * @param connectionInfo the connection whose server may have changed
	 * @return completes when the listener has dealt with it
	 */
	default Mono<Void> changesMayBeMissed(McpModernConnectionInfo connectionInfo) {
		return Mono.empty();
	}

	/**
	 * A listen stream to the server of a connection was established, which shows that the
	 * server can be reached. It is called before {@link #changesMayBeMissed}.
	 * @param connectionInfo the connection whose server accepted the stream
	 * @return completes when the listener has dealt with it
	 */
	default Mono<Void> streamEstablished(McpModernConnectionInfo connectionInfo) {
		return Mono.empty();
	}

}
