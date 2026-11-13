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

package org.springframework.ai.mcp.client.common.autoconfigure;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

import io.modelcontextprotocol.modern.client.McpAsyncClient;
import io.modelcontextprotocol.modern.client.SubscriptionEvent;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpPromptListChanged;
import org.springframework.ai.mcp.annotation.McpResourceListChanged;
import org.springframework.ai.mcp.annotation.McpToolListChanged;
import org.springframework.ai.mcp.annotation.spring.ClientMcpAsyncHandlersRegistry;
import org.springframework.ai.mcp.modern.client.AsyncModernMcpChangeListener;
import org.springframework.ai.mcp.modern.client.AsyncModernMcpConnection;
import org.springframework.ai.mcp.modern.client.McpModernConnectionInfo;
import org.springframework.util.Assert;

/**
 * Calls the reactive {@code @McpToolListChanged}, {@code @McpPromptListChanged} and
 * {@code @McpResourceListChanged} methods that name a modern connection when its server
 * reports the change on the listen stream of the connection, with the asynchronous client
 * type. It is the counterpart of {@link ModernListChangedHandlers}.
 * <p>
 * The change that a modern server reports carries no list, and the methods take one. So
 * the tools, prompts or resources are listed first, with a client of the connection that
 * is taken outside of any request: the methods see the list that the application sees,
 * not that of a caller.
 * <p>
 * The methods are also called when a stream is established again after it had ended. The
 * server may have changed in between without anyone hearing of it, as it does when it is
 * restarted with other tools. They are not called for the first stream of a connection,
 * before which nothing was known about the server.
 * <p>
 * It must hear about a change after the tool callback provider, so that a method that
 * asks the provider for its tools gets the new ones.
 *
 * @author Christian Tzolov
 */
final class AsyncModernListChangedHandlers implements AsyncModernMcpChangeListener {

	private static final Log logger = LogFactory.getLog(AsyncModernListChangedHandlers.class);

	/** The most pages of a listing that are read, as in the listing of all tools. */
	private static final int MAX_LIST_PAGES = 100;

	private static final List<Class<? extends Annotation>> LIST_CHANGED_ANNOTATIONS = List.of(McpToolListChanged.class,
			McpPromptListChanged.class, McpResourceListChanged.class);

	private final Map<String, AsyncModernMcpConnection> connections;

	private final ClientMcpAsyncHandlersRegistry handlers;

	/** One client for each connection, taken on first use, outside of any request. */
	private final Map<String, McpAsyncClient> clients = new ConcurrentHashMap<>();

	/** The connections whose listen stream was established at least once. */
	private final Set<String> establishedBefore = ConcurrentHashMap.newKeySet();

	/** The connections whose listen stream has just been established again. */
	private final Set<String> establishedAgain = ConcurrentHashMap.newKeySet();

	private AsyncModernListChangedHandlers(Map<String, AsyncModernMcpConnection> connections,
			ClientMcpAsyncHandlersRegistry handlers) {
		this.connections = Map.copyOf(connections);
		this.handlers = handlers;
	}

	/**
	 * Creates the listener for those of the given connections that a list-changed method
	 * names.
	 * @param listeningConnections the connections that have a listen stream
	 * @param handlers the registry of the annotated client handler methods
	 * @return the listener, or {@code null} if no method wants to hear about any of the
	 * connections
	 */
	static @Nullable AsyncModernListChangedHandlers forConnections(
			Collection<AsyncModernMcpConnection> listeningConnections, ClientMcpAsyncHandlersRegistry handlers) {
		Map<String, AsyncModernMcpConnection> connections = new LinkedHashMap<>();
		for (AsyncModernMcpConnection connection : listeningConnections) {
			String name = connection.info().name();
			if (LIST_CHANGED_ANNOTATIONS.stream()
				.anyMatch(annotationType -> handlers.handlesModernListChanged(name, annotationType))) {
				connections.put(name, connection);
			}
		}
		return connections.isEmpty() ? null : new AsyncModernListChangedHandlers(connections, handlers);
	}

	@Override
	public Mono<Void> changed(McpModernConnectionInfo connectionInfo, SubscriptionEvent event) {
		String name = connectionInfo.name();
		if (!this.connections.containsKey(name)) {
			return Mono.empty();
		}
		if (event instanceof SubscriptionEvent.ToolsListChanged) {
			return this.toolsChanged(name);
		}
		if (event instanceof SubscriptionEvent.PromptsListChanged) {
			return this.promptsChanged(name);
		}
		if (event instanceof SubscriptionEvent.ResourcesListChanged) {
			return this.resourcesChanged(name);
		}
		return Mono.empty();
	}

	@Override
	public Mono<Void> streamEstablished(McpModernConnectionInfo connectionInfo) {
		return Mono.fromRunnable(() -> {
			String name = connectionInfo.name();
			if (this.connections.containsKey(name) && !this.establishedBefore.add(name)) {
				this.establishedAgain.add(name);
			}
		});
	}

	/**
	 * Calls the methods of a connection whose stream has been established again. This is
	 * done here and not in {@link #streamEstablished}, because by now the tool callback
	 * provider has dropped its kept listing.
	 */
	@Override
	public Mono<Void> changesMayBeMissed(McpModernConnectionInfo connectionInfo) {
		String name = connectionInfo.name();
		return Mono.defer(() -> {
			if (!this.establishedAgain.remove(name)) {
				return Mono.empty();
			}
			List<Supplier<Mono<Void>>> listings = List.of(() -> this.toolsChanged(name),
					() -> this.promptsChanged(name), () -> this.resourcesChanged(name));
			// One listing that fails must not keep the other methods from being called.
			return Flux.fromIterable(listings).concatMap(listing -> Mono.defer(listing).onErrorResume(ex -> {
				logger.warn("The list-changed methods for the MCP connection '" + name
						+ "' could not be called after its listen stream was established again", ex);
				return Mono.empty();
			})).then();
		});
	}

	private Mono<Void> toolsChanged(String name) {
		if (!this.handlers.handlesModernListChanged(name, McpToolListChanged.class)) {
			return Mono.empty();
		}
		// Not from a listing that the client keeps: it is the one that changed.
		return Mono.defer(() -> this.client(name).listTools().skipCache().list())
			.flatMap(listing -> this.handlers.handleModernToolListChanged(name, listing.tools()));
	}

	private Mono<Void> promptsChanged(String name) {
		if (!this.handlers.handlesModernListChanged(name, McpPromptListChanged.class)) {
			return Mono.empty();
		}
		return Mono.defer(() -> {
			McpAsyncClient client = this.client(name);
			return allPages("prompts",
					cursor -> (cursor != null ? client.prompts().cursor(cursor) : client.prompts()).list()
						.map(page -> new Page<>(page.prompts(), page.nextCursor())));
		}).flatMap(prompts -> this.handlers.handleModernPromptListChanged(name, prompts));
	}

	private Mono<Void> resourcesChanged(String name) {
		if (!this.handlers.handlesModernListChanged(name, McpResourceListChanged.class)) {
			return Mono.empty();
		}
		return Mono.defer(() -> {
			McpAsyncClient client = this.client(name);
			return allPages("resources",
					cursor -> (cursor != null ? client.resources().cursor(cursor) : client.resources()).list()
						.map(page -> new Page<>(page.resources(), page.nextCursor())));
		}).flatMap(resources -> this.handlers.handleModernResourceListChanged(name, resources));
	}

	private McpAsyncClient client(String name) {
		return this.clients.computeIfAbsent(name, connectionName -> {
			AsyncModernMcpConnection connection = this.connections.get(connectionName);
			Assert.state(connection != null, () -> "No modern MCP connection named '" + connectionName + "'");
			return connection.applicationClient();
		});
	}

	/**
	 * Reads the pages of a listing until the server sends no further cursor. It fails
	 * with an {@link IllegalStateException} if the listing does not end within
	 * {@link #MAX_LIST_PAGES} pages.
	 */
	private static <T> Mono<List<T>> allPages(String what, Function<@Nullable String, Mono<Page<T>>> lister) {
		return Mono.defer(() -> {
			AtomicInteger pages = new AtomicInteger(1);
			return lister.apply(null).expand(page -> {
				String cursor = page.nextCursor();
				if (cursor == null) {
					return Mono.empty();
				}
				if (pages.incrementAndGet() > MAX_LIST_PAGES) {
					return Mono.error(new IllegalStateException(
							"The listing of the " + what + " did not end within " + MAX_LIST_PAGES + " pages"));
				}
				return lister.apply(cursor);
			}).flatMapIterable(Page::items).collectList();
		});
	}

	private record Page<T>(List<T> items, @Nullable String nextCursor) {
	}

}
