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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.modern.McpSchema.ListToolsResult;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.modern.client.McpAsyncClient;
import io.modelcontextprotocol.modern.client.SubscriptionEvent;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.support.ToolUtils;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;

/**
 * Exposes the tools of modern MCP servers as Spring AI {@link ToolCallback}s.
 * <p>
 * Each call to {@link #getToolCallbacks()} asks every {@link AsyncModernMcpConnection}
 * for the client that serves the current request, lists that client's tools, and returns
 * callbacks bound to that client. A {@code ChatClient} calls this method once for each
 * request, so register the provider with {@code defaultToolCallbacks(provider)} rather
 * than the callbacks it returns.
 * <p>
 * The server decides how long a tool listing may be kept and who may share it, with the
 * {@code ttlMs} and {@code cacheScope} of the listing:
 * <ul>
 * <li>A listing with a {@code ttlMs} of zero is never kept, and every call to
 * {@link #getToolCallbacks()} lists the tools again. Servers send zero unless configured
 * otherwise.</li>
 * <li>A {@code public} listing is the same for every caller. The provider keeps it for
 * its {@code ttlMs} and uses it for all requests, whichever client serves them.</li>
 * <li>A {@code private} listing may differ from caller to caller, so the provider does
 * not keep it. It is only kept by a client that outlives a request, as the client of a
 * {@link AsyncModernMcpConnection#shared shared} connection does, or the client that a
 * {@link AsyncModernMcpConnection#scoped scoped} connection keeps for a user. Use a
 * shared connection when the application calls the server under a single identity.</li>
 * </ul>
 * A tool call is never served from a kept listing: it always goes to the server, through
 * the client of the request that resolved the callback.
 * <p>
 * A kept listing is used until its {@code ttlMs} has passed. To drop it as soon as the
 * server reports that its tools changed, register the provider as a listener of an
 * {@link AsyncModernMcpChangeSubscriber}, which keeps a listen stream open to the server.
 * <p>
 * The tools are listed through the asynchronous client, and the call blocks until the
 * listings are there, as the {@link ToolCallbackProvider} contract requires. So it must
 * not be made on a thread that may not block, such as an event loop. The caller of a
 * listing is the {@link AsyncModernMcpConnection#transportContext() transport context} of
 * its connection, which is read on the calling thread.
 * <p>
 * A server that cannot be reached does not fail the call: its tools are left out and a
 * warning is logged, so the tools of the other connections stay usable. A server counts
 * as unreachable when the connection cannot be established, the exchange times out or
 * breaks, or the server answers with a {@code 5xx}, {@code 408} or {@code 429} status.
 * Every other failure is thrown, in particular an authorization failure, so that the
 * application can ask the user to log in again.
 * <p>
 * A server that could not be reached is not asked again until a cool-down time has passed
 * (30 seconds unless set otherwise). Otherwise a server that accepts connections but does
 * not answer would hold up every request for the whole timeout of the listing.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 * @see AsyncModernMcpConnection
 * @see AsyncModernMcpToolCallback
 */
public final class AsyncModernMcpToolCallbackProvider
		implements ToolCallbackProvider, AsyncModernMcpChangeListener, Ordered {

	private static final Log logger = LogFactory.getLog(AsyncModernMcpToolCallbackProvider.class);

	private static final Duration DEFAULT_UNREACHABLE_COOL_DOWN = Duration.ofSeconds(30);

	private final List<AsyncModernMcpConnection> connections;

	/** What is kept of the tool listings, and which servers are left alone for now. */
	private final ModernToolListings<McpAsyncClient> listings;

	private final McpModernToolFilter toolFilter;

	private final McpModernToolNamePrefixGenerator toolNamePrefixGenerator;

	private final ToolContextToMcpMetaConverter toolContextToMcpMetaConverter;

	private final @Nullable AsyncModernMcpToolCallCustomizer toolCallCustomizer;

	private AsyncModernMcpToolCallbackProvider(List<AsyncModernMcpConnection> connections,
			McpModernToolFilter toolFilter, McpModernToolNamePrefixGenerator toolNamePrefixGenerator,
			ToolContextToMcpMetaConverter toolContextToMcpMetaConverter,
			@Nullable AsyncModernMcpToolCallCustomizer toolCallCustomizer, Duration unreachableCoolDown,
			LongSupplier nanoClock) {
		Assert.notNull(connections, "connections must not be null");
		Assert.notNull(toolFilter, "toolFilter must not be null");
		Assert.notNull(toolNamePrefixGenerator, "toolNamePrefixGenerator must not be null");
		Assert.notNull(toolContextToMcpMetaConverter, "toolContextToMcpMetaConverter must not be null");
		this.connections = List.copyOf(connections);
		this.toolFilter = toolFilter;
		this.toolNamePrefixGenerator = toolNamePrefixGenerator;
		this.toolContextToMcpMetaConverter = toolContextToMcpMetaConverter;
		this.toolCallCustomizer = toolCallCustomizer;
		this.listings = new ModernToolListings<>(logger, "AsyncModernMcpClientScope", unreachableCoolDown, nanoClock);
	}

	/**
	 * Creates a builder for a {@link AsyncModernMcpToolCallbackProvider}.
	 * @return the builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Returns a callback for each tool of every connection that the filter accepts. The
	 * tools of a connection are listed unless a public listing of them is still kept. The
	 * callbacks of a connection are bound to the client that connection returned for this
	 * call. The tools of a connection whose server cannot be reached are left out, and
	 * that server is not asked again until the cool-down time has passed.
	 * @return the tool callbacks
	 * @throws IllegalStateException if two tools end up with the same name
	 */
	@Override
	public ToolCallback[] getToolCallbacks() {
		List<ToolCallback> toolCallbacks = new ArrayList<>();
		for (AsyncModernMcpConnection connection : this.connections) {
			McpModernConnectionInfo connectionInfo = connection.info();
			if (this.listings.isCoolingDown(connection)) {
				continue;
			}
			McpAsyncClient mcpClient = connection.client();
			// The caller of this request, for the listing and for the tool calls.
			McpTransportContext caller = connection.transportContext();
			List<Tool> tools = this.listings.tools(connection, connectionInfo, connection.clientPerRequest(), mcpClient,
					(client, fromServer) -> list(client, fromServer, caller));
			if (tools == null) {
				// The server cannot be reached.
				continue;
			}
			for (Tool tool : tools) {
				if (this.toolFilter.test(connectionInfo, tool)) {
					AsyncModernMcpToolCallback.Builder toolCallback = AsyncModernMcpToolCallback.builder()
						.mcpClient(mcpClient)
						.tool(tool)
						.prefixedToolName(this.toolNamePrefixGenerator.prefixedToolName(connectionInfo, tool))
						.toolContextToMcpMetaConverter(this.toolContextToMcpMetaConverter)
						.connectionInfo(connectionInfo)
						.transportContext(caller);
					if (this.toolCallCustomizer != null) {
						toolCallback.toolCallCustomizer(this.toolCallCustomizer);
					}
					toolCallbacks.add(toolCallback.build());
				}
			}
		}
		List<String> duplicateToolNames = ToolUtils.getDuplicateToolNames(toolCallbacks);
		if (!duplicateToolNames.isEmpty()) {
			throw new IllegalStateException(
					"Multiple tools with the same name (%s)".formatted(String.join(", ", duplicateToolNames)));
		}
		return toolCallbacks.toArray(new ToolCallback[0]);
	}

	private static ListToolsResult list(McpAsyncClient mcpClient, boolean fromServer, McpTransportContext caller) {
		Mono<ListToolsResult> listing = fromServer ? mcpClient.listTools().skipCache().list()
				: mcpClient.listTools().list();
		ListToolsResult result = listing.contextWrite(context -> context.put(McpTransportContext.KEY, caller)).block();
		Assert.state(result != null, "The tool listing returned no result");
		return result;
	}

	/**
	 * Drops the kept tool listing of a connection, so that the next call to
	 * {@link #getToolCallbacks()} lists its tools again. Use it when the tools of the
	 * server are known to have changed before the {@code ttlMs} of the listing has
	 * passed. A client that keeps a listing itself, as a shared client or a client that a
	 * {@link AsyncModernMcpClientScope} keeps for a user does, asks the server the next
	 * time it lists the tools.
	 * @param connectionName the name of the connection
	 */
	public void invalidateToolListing(String connectionName) {
		Assert.notNull(connectionName, "connectionName must not be null");
		this.listings.invalidate(connectionName,
				this.connections.stream()
					.filter(connection -> connection.info().name().equals(connectionName))
					.toList());
	}

	/**
	 * The provider hears about a change before the other listeners of an
	 * {@link AsyncModernMcpChangeSubscriber}, so that a listener that asks it for the
	 * tools finds the kept listing dropped.
	 * @return the highest precedence
	 */
	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	/**
	 * Ends the cool-down of the connection, if its server could not be reached before: a
	 * listen stream that was established shows that it can be reached again.
	 */
	@Override
	public Mono<Void> streamEstablished(McpModernConnectionInfo connectionInfo) {
		return Mono.fromRunnable(() -> {
			for (AsyncModernMcpConnection connection : this.connections) {
				if (connection.info().name().equals(connectionInfo.name())) {
					this.listings.serverReachable(connection, connection.info());
				}
			}
		});
	}

	/**
	 * Drops the kept tool listing of the connection when its server reports that its
	 * tools changed.
	 */
	@Override
	public Mono<Void> changed(McpModernConnectionInfo connectionInfo, SubscriptionEvent event) {
		return Mono.fromRunnable(() -> {
			if (event instanceof SubscriptionEvent.ToolsListChanged) {
				this.invalidateToolListing(connectionInfo.name());
			}
		});
	}

	/**
	 * Drops the kept tool listing of the connection, because a change of its tools may
	 * have gone unreported.
	 */
	@Override
	public Mono<Void> changesMayBeMissed(McpModernConnectionInfo connectionInfo) {
		return Mono.fromRunnable(() -> this.invalidateToolListing(connectionInfo.name()));
	}

	/**
	 * Builder for {@link AsyncModernMcpToolCallbackProvider}.
	 */
	public static final class Builder {

		private final List<AsyncModernMcpConnection> connections = new ArrayList<>();

		private McpModernToolFilter toolFilter = McpModernToolFilter.acceptAll();

		private McpModernToolNamePrefixGenerator toolNamePrefixGenerator = McpModernToolNamePrefixGenerator
			.connectionPrefix();

		private ToolContextToMcpMetaConverter toolContextToMcpMetaConverter = ToolContextToMcpMetaConverter
			.defaultConverter();

		private @Nullable AsyncModernMcpToolCallCustomizer toolCallCustomizer;

		private Duration unreachableCoolDown = DEFAULT_UNREACHABLE_COOL_DOWN;

		private LongSupplier nanoClock = System::nanoTime;

		private Builder() {
		}

		/**
		 * Sets the connections whose tools are exposed, replacing those set before.
		 * @param connections the connections
		 * @return this builder
		 */
		public Builder connections(List<AsyncModernMcpConnection> connections) {
			Assert.notNull(connections, "connections must not be null");
			this.connections.clear();
			this.connections.addAll(connections);
			return this;
		}

		/**
		 * Adds a connection whose tools are exposed.
		 * @param connection the connection
		 * @return this builder
		 */
		public Builder connection(AsyncModernMcpConnection connection) {
			Assert.notNull(connection, "connection must not be null");
			this.connections.add(connection);
			return this;
		}

		/**
		 * Sets the filter that decides which tools are exposed. Defaults to
		 * {@link McpModernToolFilter#acceptAll()}.
		 * @param toolFilter the tool filter
		 * @return this builder
		 */
		public Builder toolFilter(McpModernToolFilter toolFilter) {
			Assert.notNull(toolFilter, "toolFilter must not be null");
			this.toolFilter = toolFilter;
			return this;
		}

		/**
		 * Sets the generator of the tool names presented to the model. Defaults to
		 * {@link McpModernToolNamePrefixGenerator#connectionPrefix()}.
		 * @param toolNamePrefixGenerator the tool name generator
		 * @return this builder
		 */
		public Builder toolNamePrefixGenerator(McpModernToolNamePrefixGenerator toolNamePrefixGenerator) {
			Assert.notNull(toolNamePrefixGenerator, "toolNamePrefixGenerator must not be null");
			this.toolNamePrefixGenerator = toolNamePrefixGenerator;
			return this;
		}

		/**
		 * Sets the converter that turns {@code ToolContext} entries into the
		 * {@code _meta} entries of a tool call.
		 * @param toolContextToMcpMetaConverter the converter
		 * @return this builder
		 */
		public Builder toolContextToMcpMetaConverter(ToolContextToMcpMetaConverter toolContextToMcpMetaConverter) {
			Assert.notNull(toolContextToMcpMetaConverter, "toolContextToMcpMetaConverter must not be null");
			this.toolContextToMcpMetaConverter = toolContextToMcpMetaConverter;
			return this;
		}

		/**
		 * Sets the customizer that is applied to every tool call, with the
		 * {@code ToolContext} of that call. Use it to set what depends on the call, such
		 * as an elicitation handler that needs to know the user it has to ask.
		 * @param toolCallCustomizer the customizer
		 * @return this builder
		 */
		public Builder toolCallCustomizer(AsyncModernMcpToolCallCustomizer toolCallCustomizer) {
			Assert.notNull(toolCallCustomizer, "toolCallCustomizer must not be null");
			this.toolCallCustomizer = toolCallCustomizer;
			return this;
		}

		/**
		 * Sets how long a server that could not be reached is left alone before it is
		 * asked for its tools again. During that time its tools are left out without
		 * contacting it. Defaults to 30 seconds; zero asks the server on every call.
		 * @param unreachableCoolDown the cool-down time, not negative
		 * @return this builder
		 */
		public Builder unreachableCoolDown(Duration unreachableCoolDown) {
			Assert.notNull(unreachableCoolDown, "unreachableCoolDown must not be null");
			Assert.isTrue(!unreachableCoolDown.isNegative(), "unreachableCoolDown must not be negative");
			this.unreachableCoolDown = unreachableCoolDown;
			return this;
		}

		/**
		 * Sets the clock that kept listings and cool-downs are timed with, in
		 * nanoseconds. For tests.
		 */
		Builder nanoClock(LongSupplier nanoClock) {
			this.nanoClock = nanoClock;
			return this;
		}

		/**
		 * Builds the provider.
		 * @return the provider
		 */
		public AsyncModernMcpToolCallbackProvider build() {
			return new AsyncModernMcpToolCallbackProvider(this.connections, this.toolFilter,
					this.toolNamePrefixGenerator, this.toolContextToMcpMetaConverter, this.toolCallCustomizer,
					this.unreachableCoolDown, this.nanoClock);
		}

	}

}
