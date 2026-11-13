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

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import io.modelcontextprotocol.modern.McpSchema.CacheScope;
import io.modelcontextprotocol.modern.McpSchema.ListToolsResult;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.modern.client.McpResponseLostException;
import io.modelcontextprotocol.modern.client.McpSyncClient;
import io.modelcontextprotocol.modern.client.McpTimeoutException;
import io.modelcontextprotocol.modern.client.SubscriptionEvent;
import io.modelcontextprotocol.modern.client.transport.McpHttpStatusException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.Exceptions;

import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.support.ToolUtils;
import org.springframework.util.Assert;

/**
 * Exposes the tools of modern MCP servers as Spring AI {@link ToolCallback}s.
 * <p>
 * Each call to {@link #getToolCallbacks()} asks every {@link SyncModernMcpConnection} for
 * the client that serves the current request, lists that client's tools, and returns
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
 * not keep it. It is only kept by a client that serves all requests, as the client of a
 * {@link SyncModernMcpConnection#shared shared} connection does; use such a connection
 * when the application calls the server under a single identity.</li>
 * </ul>
 * A tool call is never served from a kept listing: it always goes to the server, through
 * the client of the request that resolved the callback.
 * <p>
 * A kept listing is used until its {@code ttlMs} has passed. To drop it as soon as the
 * server reports that its tools changed, register the provider as a listener of a
 * {@link SyncModernMcpChangeSubscriber}, which keeps a listen stream open to the server.
 * <p>
 * A server that cannot be reached does not fail the call: its tools are left out and a
 * warning is logged, so the tools of the other connections stay usable. A server counts
 * as unreachable when the connection cannot be established, the exchange times out or
 * breaks, or a gateway answers {@code 502}, {@code 503} or {@code 504}. Every other
 * failure is thrown, in particular an authorization failure, so that the application can
 * ask the user to log in again.
 * <p>
 * A server that could not be reached is not asked again until a cool-down time has passed
 * (30 seconds unless set otherwise). Otherwise a server that accepts connections but does
 * not answer would hold up every request for the whole timeout of the listing.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 * @see SyncModernMcpConnection
 * @see SyncModernMcpToolCallback
 */
public final class SyncModernMcpToolCallbackProvider implements ToolCallbackProvider, SyncModernMcpChangeListener {

	private static final Log logger = LogFactory.getLog(SyncModernMcpToolCallbackProvider.class);

	private static final Set<Integer> UNAVAILABLE_HTTP_STATUSES = Set.of(502, 503, 504);

	private static final Duration DEFAULT_UNREACHABLE_COOL_DOWN = Duration.ofSeconds(30);

	private final List<SyncModernMcpConnection> connections;

	/**
	 * The connections whose server was unreachable at the last listing, with the time of
	 * that listing. A server is not asked again during the cool-down, and an outage is
	 * logged as a warning once and not for every request.
	 */
	private final Map<SyncModernMcpConnection, Long> unreachableConnections = new ConcurrentHashMap<>();

	private final long unreachableCoolDownNanos;

	/**
	 * The public tool listings that may still be used, by connection.
	 */
	private final Map<SyncModernMcpConnection, KeptListing> keptListings = new ConcurrentHashMap<>();

	/**
	 * Counts the dropped listings of each connection, by its name, so that a listing that
	 * was under way when the server reported a change is not kept.
	 */
	private final Map<String, AtomicLong> invalidations = new ConcurrentHashMap<>();

	/**
	 * The connections whose next listing must come from the server and not from a copy
	 * that the client itself keeps, as the client of a shared connection does.
	 */
	private final Set<SyncModernMcpConnection> listFromServer = ConcurrentHashMap.newKeySet();

	private final LongSupplier nanoClock;

	private final McpModernToolFilter toolFilter;

	private final McpModernToolNamePrefixGenerator toolNamePrefixGenerator;

	private final ToolContextToMcpMetaConverter toolContextToMcpMetaConverter;

	private final @Nullable SyncModernMcpToolCallCustomizer toolCallCustomizer;

	private SyncModernMcpToolCallbackProvider(List<SyncModernMcpConnection> connections, McpModernToolFilter toolFilter,
			McpModernToolNamePrefixGenerator toolNamePrefixGenerator,
			ToolContextToMcpMetaConverter toolContextToMcpMetaConverter,
			@Nullable SyncModernMcpToolCallCustomizer toolCallCustomizer, Duration unreachableCoolDown,
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
		this.unreachableCoolDownNanos = unreachableCoolDown.toNanos();
		this.nanoClock = nanoClock;
	}

	/**
	 * Creates a builder for a {@link SyncModernMcpToolCallbackProvider}.
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
		for (SyncModernMcpConnection connection : this.connections) {
			McpModernConnectionInfo connectionInfo = connection.info();
			if (this.isCoolingDown(connection)) {
				continue;
			}
			McpSyncClient mcpClient = connection.client();
			List<Tool> tools;
			try {
				tools = this.tools(connection, mcpClient);
			}
			catch (RuntimeException ex) {
				if (!isServerUnreachable(ex)) {
					throw ex;
				}
				this.serverUnreachable(connection, ex);
				continue;
			}
			this.serverReachable(connection);
			for (Tool tool : tools) {
				if (this.toolFilter.test(connectionInfo, tool)) {
					SyncModernMcpToolCallback.Builder toolCallback = SyncModernMcpToolCallback.builder()
						.mcpClient(mcpClient)
						.tool(tool)
						.prefixedToolName(this.toolNamePrefixGenerator.prefixedToolName(connectionInfo, tool))
						.toolContextToMcpMetaConverter(this.toolContextToMcpMetaConverter)
						.connectionInfo(connectionInfo);
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

	/**
	 * The tools of a connection, from the kept listing if there is one and from the
	 * server otherwise.
	 */
	private List<Tool> tools(SyncModernMcpConnection connection, McpSyncClient mcpClient) {
		long now = this.nanoClock.getAsLong();
		KeptListing kept = this.keptListings.get(connection);
		boolean fromServer = false;
		if (kept != null) {
			if (kept.isFresh(now)) {
				return kept.tools();
			}
			this.keptListings.remove(connection, kept);
			// The client of a shared connection keeps this listing as well, and its copy
			// is as old as the one that has just expired here.
			fromServer = true;
		}
		// A dropped listing must not be answered from the client's copy either.
		fromServer = this.listFromServer.remove(connection) || fromServer;

		AtomicLong invalidations = this.invalidations.computeIfAbsent(connection.info().name(),
				name -> new AtomicLong());
		long invalidationsBefore = invalidations.get();
		ListToolsResult listing;
		try {
			listing = fromServer ? mcpClient.listTools().skipCache().list() : mcpClient.listTools().list();
		}
		catch (RuntimeException ex) {
			if (fromServer) {
				// The next listing still has to come from the server.
				this.listFromServer.add(connection);
			}
			throw ex;
		}
		// Only a public listing may be handed to other callers. A private one is left to
		// the client, which keeps it for as long as that client lives.
		if (listing.ttlMs() > 0 && listing.cacheScope() == CacheScope.PUBLIC
				&& invalidations.get() == invalidationsBefore) {
			KeptListing keptListing = new KeptListing(listing.tools(), now,
					TimeUnit.MILLISECONDS.toNanos(listing.ttlMs()));
			this.keptListings.put(connection, keptListing);
			if (invalidations.get() != invalidationsBefore) {
				// The tools changed while the listing was being kept.
				this.keptListings.remove(connection, keptListing);
			}
		}
		return listing.tools();
	}

	/**
	 * Drops the kept tool listing of a connection, so that the next call to
	 * {@link #getToolCallbacks()} lists its tools again. Use it when the tools of the
	 * server are known to have changed before the {@code ttlMs} of the listing has
	 * passed.
	 * @param connectionName the name of the connection
	 */
	public void invalidateToolListing(String connectionName) {
		Assert.notNull(connectionName, "connectionName must not be null");
		this.invalidations.computeIfAbsent(connectionName, name -> new AtomicLong()).incrementAndGet();
		for (SyncModernMcpConnection connection : this.connections) {
			if (connection.info().name().equals(connectionName)) {
				// Also for a listing that only the client keeps, as a private one of a
				// shared connection.
				this.listFromServer.add(connection);
				this.keptListings.remove(connection);
			}
		}
	}

	/**
	 * Ends the cool-down of the connection, if its server could not be reached before: a
	 * listen stream that was established shows that it can be reached again.
	 */
	@Override
	public void streamEstablished(McpModernConnectionInfo connectionInfo) {
		for (SyncModernMcpConnection connection : this.connections) {
			if (connection.info().name().equals(connectionInfo.name())) {
				this.serverReachable(connection);
			}
		}
	}

	/**
	 * Drops the kept tool listing of the connection when its server reports that its
	 * tools changed.
	 */
	@Override
	public void changed(McpModernConnectionInfo connectionInfo, SubscriptionEvent event) {
		if (event instanceof SubscriptionEvent.ToolsListChanged) {
			this.invalidateToolListing(connectionInfo.name());
		}
	}

	/**
	 * Drops the kept tool listing of the connection, because a change of its tools may
	 * have gone unreported.
	 */
	@Override
	public void changesMayBeMissed(McpModernConnectionInfo connectionInfo) {
		this.invalidateToolListing(connectionInfo.name());
	}

	/**
	 * Whether the failure of a listing means that the server cannot be reached, as
	 * opposed to a server that answered with an error or a caller that is not authorized.
	 */
	private static boolean isServerUnreachable(RuntimeException ex) {
		// The synchronous client wraps checked exceptions, such as a refused connection.
		Throwable failure = Exceptions.unwrap(ex);
		if (failure instanceof McpHttpStatusException statusException) {
			return UNAVAILABLE_HTTP_STATUSES.contains(statusException.statusCode());
		}
		return failure instanceof IOException || failure instanceof McpTimeoutException
				|| failure instanceof McpResponseLostException;
	}

	/**
	 * Whether the server of the connection could not be reached a short time ago, in
	 * which case it is not asked now.
	 */
	private boolean isCoolingDown(SyncModernMcpConnection connection) {
		Long unreachableAtNanos = this.unreachableConnections.get(connection);
		return unreachableAtNanos != null
				&& this.nanoClock.getAsLong() - unreachableAtNanos < this.unreachableCoolDownNanos;
	}

	private void serverUnreachable(SyncModernMcpConnection connection, RuntimeException ex) {
		String message = "The MCP server of the connection '" + connection.info().name()
				+ "' cannot be reached; its tools are left out until it can be reached again";
		// The time is taken after the failed listing, which may have waited for a
		// timeout.
		if (this.unreachableConnections.put(connection, this.nanoClock.getAsLong()) == null) {
			logger.warn(message, ex);
		}
		else if (logger.isDebugEnabled()) {
			logger.debug(message + ": " + Exceptions.unwrap(ex));
		}
	}

	private void serverReachable(SyncModernMcpConnection connection) {
		if (this.unreachableConnections.remove(connection) != null) {
			logger.info("The MCP server of the connection '" + connection.info().name() + "' can be reached again");
		}
	}

	/**
	 * A public tool listing and the time it may be used for.
	 */
	private record KeptListing(List<Tool> tools, long keptAtNanos, long ttlNanos) {

		boolean isFresh(long nowNanos) {
			return nowNanos - this.keptAtNanos < this.ttlNanos;
		}

	}

	/**
	 * Builder for {@link SyncModernMcpToolCallbackProvider}.
	 */
	public static final class Builder {

		private final List<SyncModernMcpConnection> connections = new ArrayList<>();

		private McpModernToolFilter toolFilter = McpModernToolFilter.acceptAll();

		private McpModernToolNamePrefixGenerator toolNamePrefixGenerator = McpModernToolNamePrefixGenerator
			.connectionPrefix();

		private ToolContextToMcpMetaConverter toolContextToMcpMetaConverter = ToolContextToMcpMetaConverter
			.defaultConverter();

		private @Nullable SyncModernMcpToolCallCustomizer toolCallCustomizer;

		private Duration unreachableCoolDown = DEFAULT_UNREACHABLE_COOL_DOWN;

		private LongSupplier nanoClock = System::nanoTime;

		private Builder() {
		}

		/**
		 * Sets the connections whose tools are exposed, replacing those set before.
		 * @param connections the connections
		 * @return this builder
		 */
		public Builder connections(List<SyncModernMcpConnection> connections) {
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
		public Builder connection(SyncModernMcpConnection connection) {
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
		public Builder toolCallCustomizer(SyncModernMcpToolCallCustomizer toolCallCustomizer) {
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
		public SyncModernMcpToolCallbackProvider build() {
			return new SyncModernMcpToolCallbackProvider(this.connections, this.toolFilter,
					this.toolNamePrefixGenerator, this.toolContextToMcpMetaConverter, this.toolCallCustomizer,
					this.unreachableCoolDown, this.nanoClock);
		}

	}

}
