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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import io.modelcontextprotocol.modern.McpSchema.CacheScope;
import io.modelcontextprotocol.modern.McpSchema.ListToolsResult;
import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.modern.client.McpResponseLostException;
import io.modelcontextprotocol.modern.client.McpTimeoutException;
import io.modelcontextprotocol.modern.client.transport.McpHttpStatusException;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import reactor.core.Exceptions;
import reactor.core.scheduler.Schedulers;

import org.springframework.util.Assert;

/**
 * The tool listings of the modern connections of a tool callback provider: which listing
 * may be used again, which must come from the server, and which server is left alone
 * because it could not be reached. The synchronous and the asynchronous provider share
 * it, and differ only in how a client lists its tools.
 *
 * @param <C> the type of the client that lists the tools
 * @author Christian Tzolov
 */
final class ModernToolListings<C> {

	/** The statuses below 500 that also say that a server cannot serve for the moment. */
	private static final Set<Integer> UNAVAILABLE_HTTP_STATUSES = Set.of(408, 429);

	private final Log logger;

	/** The type that selects the clients of a scoped connection, for the hint. */
	private final String clientScopeTypeName;

	/**
	 * The connections whose server was unreachable at the last listing, with the time of
	 * that listing. A server is not asked again during the cool-down, and an outage is
	 * logged as a warning once and not for every request.
	 */
	private final Map<Object, Long> unreachableConnections = new ConcurrentHashMap<>();

	private final long unreachableCoolDownNanos;

	/**
	 * The public tool listings that may still be used, by connection.
	 */
	private final Map<Object, KeptListing> keptListings = new ConcurrentHashMap<>();

	/**
	 * Counts the dropped listings of each connection, by its name, so that a listing that
	 * was under way when the server reported a change is not kept, and so that each
	 * client knows whether the listing it keeps itself is still the latest.
	 */
	private final Map<String, AtomicLong> invalidations = new ConcurrentHashMap<>();

	/**
	 * The count of dropped listings of its connection at which each client last listed
	 * the tools. A client that listed them before the latest drop may answer from a copy
	 * that it keeps itself, as a shared client or the client that a scope keeps for a
	 * user does, so its next listing must come from the server. A connection can have
	 * many clients, and each of them has to find that out for itself. The clients are
	 * held weakly: one for a single request is gone with that request.
	 */
	private final Map<C, Long> listedAtInvalidations = Collections.synchronizedMap(new WeakHashMap<>());

	/**
	 * The connections that were told that a private listing could be kept with another
	 * kind of connection.
	 */
	private final Set<String> privateListingHints = ConcurrentHashMap.newKeySet();

	private final LongSupplier nanoClock;

	ModernToolListings(Log logger, String clientScopeTypeName, Duration unreachableCoolDown, LongSupplier nanoClock) {
		Assert.notNull(unreachableCoolDown, "unreachableCoolDown must not be null");
		Assert.isTrue(!unreachableCoolDown.isNegative(), "unreachableCoolDown must not be negative");
		Assert.notNull(nanoClock, "nanoClock must not be null");
		this.logger = logger;
		this.clientScopeTypeName = clientScopeTypeName;
		this.unreachableCoolDownNanos = unreachableCoolDown.toNanos();
		this.nanoClock = nanoClock;
	}

	/**
	 * Whether the server of the connection could not be reached a short time ago, in
	 * which case it is not asked now.
	 * @param connection the connection
	 * @return {@code true} if the tools of the connection are to be left out
	 */
	boolean isCoolingDown(Object connection) {
		Long unreachableAtNanos = this.unreachableConnections.get(connection);
		return unreachableAtNanos != null
				&& this.nanoClock.getAsLong() - unreachableAtNanos < this.unreachableCoolDownNanos;
	}

	/**
	 * The tools of a connection for the given client, from the kept listing if there is
	 * one and from the server otherwise. A server that cannot be reached is remembered,
	 * and its tools are left out.
	 * @param connection the connection, which is the key of what is kept for it
	 * @param connectionInfo describes the connection
	 * @param clientPerRequest whether the client serves this request only
	 * @param client the client of the request
	 * @param lister lists the tools with a client
	 * @return the tools, or {@code null} if the server cannot be reached
	 * @throws RuntimeException the failure of a listing that does not mean that the
	 * server cannot be reached
	 */
	@Nullable List<Tool> tools(Object connection, McpModernConnectionInfo connectionInfo, boolean clientPerRequest, C client,
			Lister<C> lister) {
		List<Tool> tools;
		try {
			tools = this.list(connection, connectionInfo, clientPerRequest, client, lister);
		}
		catch (RuntimeException ex) {
			if (!isServerUnreachable(ex)) {
				throw ex;
			}
			this.serverUnreachable(connection, connectionInfo, ex);
			return null;
		}
		this.serverReachable(connection, connectionInfo);
		return tools;
	}

	private List<Tool> list(Object connection, McpModernConnectionInfo connectionInfo, boolean clientPerRequest,
			C client, Lister<C> lister) {
		long now = this.nanoClock.getAsLong();
		AtomicLong invalidations = this.invalidations.computeIfAbsent(connectionInfo.name(), name -> new AtomicLong());
		KeptListing kept = this.keptListings.get(connection);
		boolean fromServer = false;
		if (kept != null) {
			if (kept.isFresh(now)) {
				return kept.tools();
			}
			// A client that keeps this listing as well has a copy that is as old as the
			// one that has just expired here. So the expiry counts as a dropped listing,
			// which makes every such client ask the server.
			if (this.keptListings.remove(connection, kept)) {
				invalidations.incrementAndGet();
			}
			fromServer = true;
		}
		// The count is read before the listing: a drop during the listing changes it,
		// and the listing is then not kept.
		long invalidationsBefore = invalidations.get();
		// A listing that was dropped must not be answered from the client's own copy.
		// A client for one request has no copy and is not remembered. One that is not
		// known here may have listed the tools elsewhere, so it asks the server once a
		// listing was dropped.
		boolean remembered = !clientPerRequest;
		Long listedAt = remembered ? this.listedAtInvalidations.get(client) : null;
		fromServer = fromServer || (listedAt != null ? listedAt < invalidationsBefore : invalidationsBefore > 0);
		boolean nonBlockingThread = Schedulers.isInNonBlockingThread();
		if (nonBlockingThread && (fromServer || clientPerRequest)) {
			// The listing certainly asks the server and blocks, which Reactor refuses on
			// such a thread with a message that does not say what to do. A client that
			// outlives the request may answer from the listing it keeps, without
			// blocking,
			// so it is asked.
			throw blockingListingRefused(connectionInfo, null);
		}
		ListToolsResult listing;
		try {
			listing = lister.list(client, fromServer);
		}
		catch (IllegalStateException ex) {
			if (nonBlockingThread && isBlockingRefused(ex)) {
				throw blockingListingRefused(connectionInfo, ex);
			}
			throw ex;
		}
		if (remembered) {
			// Not reached if the listing fails: the client then asks the server again.
			this.listedAtInvalidations.put(client, invalidationsBefore);
		}
		if (listing.ttlMs() > 0 && listing.cacheScope() != CacheScope.PUBLIC && clientPerRequest
				&& this.privateListingHints.add(connectionInfo.name())) {
			// Said once for a connection: the server allows a listing to be kept that
			// this kind of connection cannot keep.
			logger.info("The MCP server of the connection '" + connectionInfo.name()
					+ "' allows its tool listing to be kept for " + listing.ttlMs() + " ms, but marks it as "
					+ "private, so only the client that listed it may keep it. The connection creates a client for "
					+ "each request, so its tools are listed for each request. If the application calls the server "
					+ "under a single identity, use a shared connection (client-scope=SHARED). To keep the listing "
					+ "for each user, use a scoped connection (client-scope=CUSTOM with a " + this.clientScopeTypeName
					+ ").");
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
	 * Drops what is kept of the tools of the connections with the given name. A client
	 * that keeps a listing itself asks the server the next time it lists the tools.
	 * @param connectionName the name of the connections
	 * @param connections the connections with that name
	 */
	void invalidate(String connectionName, Iterable<?> connections) {
		// The count also tells each client that the listing it keeps itself is dropped.
		this.invalidations.computeIfAbsent(connectionName, name -> new AtomicLong()).incrementAndGet();
		connections.forEach(this.keptListings::remove);
	}

	/**
	 * Ends the cool-down of the connection, if its server could not be reached before.
	 * @param connection the connection
	 * @param connectionInfo describes the connection
	 */
	void serverReachable(Object connection, McpModernConnectionInfo connectionInfo) {
		if (this.unreachableConnections.remove(connection) != null) {
			logger.info("The MCP server of the connection '" + connectionInfo.name() + "' can be reached again");
		}
	}

	private void serverUnreachable(Object connection, McpModernConnectionInfo connectionInfo, RuntimeException ex) {
		String message = "The MCP server of the connection '" + connectionInfo.name()
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

	private static IllegalStateException blockingListingRefused(McpModernConnectionInfo connectionInfo,
			@Nullable Throwable cause) {
		return new IllegalStateException("The tools of the MCP connection '" + connectionInfo.name()
				+ "' are listed with a blocking call, which is not possible on the non-blocking thread '"
				+ Thread.currentThread().getName() + "'. Resolve the tools on a thread that may block: start the "
				+ "ChatClient call on one, for example with subscribeOn(Schedulers.boundedElastic()).", cause);
	}

	/**
	 * Whether Reactor refused to block on the current thread.
	 */
	private static boolean isBlockingRefused(IllegalStateException ex) {
		String message = ex.getMessage();
		return message != null && message.contains("are blocking, which is not supported in thread");
	}

	/**
	 * Whether the failure of a listing means that the server cannot be reached, as
	 * opposed to a server that answered with an error or a caller that is not authorized.
	 */
	private static boolean isServerUnreachable(RuntimeException ex) {
		// A blocking call wraps checked exceptions, such as a refused connection.
		Throwable failure = Exceptions.unwrap(ex);
		if (failure instanceof McpHttpStatusException statusException) {
			// A failure of the server, or one that asks to come back later. A status
			// that the caller must act on, such as 401, 403 or 404, is not among them.
			int status = statusException.statusCode();
			return status >= 500 || UNAVAILABLE_HTTP_STATUSES.contains(status);
		}
		return failure instanceof IOException || failure instanceof McpTimeoutException
				|| failure instanceof McpResponseLostException;
	}

	/**
	 * Lists the tools of a server with a client.
	 *
	 * @param <C> the type of the client
	 */
	@FunctionalInterface
	interface Lister<C> {

		/**
		 * Lists the tools.
		 * @param client the client to list them with
		 * @param fromServer whether the listing must come from the server and not from a
		 * copy that the client keeps
		 * @return the listing
		 */
		ListToolsResult list(C client, boolean fromServer);

	}

	/**
	 * A public tool listing and the time it may be used for.
	 */
	private record KeptListing(List<Tool> tools, long keptAtNanos, long ttlNanos) {

		boolean isFresh(long nowNanos) {
			return nowNanos - this.keptAtNanos < this.ttlNanos;
		}

	}

}
