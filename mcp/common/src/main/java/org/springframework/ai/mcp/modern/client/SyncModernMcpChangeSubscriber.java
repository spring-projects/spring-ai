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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.modelcontextprotocol.modern.client.McpSyncClient;
import io.modelcontextprotocol.modern.client.McpSyncSubscriptionListener;
import io.modelcontextprotocol.modern.client.SubscriptionEvent;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.SmartLifecycle;
import org.springframework.util.Assert;

/**
 * Keeps a listen stream open to each of its modern MCP connections and tells its
 * {@link SyncModernMcpChangeListener listeners} what the servers report.
 * <p>
 * A listen stream is a long-lived request, so it is not opened by the client of a chat
 * request. Each connection gets one client for its stream, taken from
 * {@link SyncModernMcpConnection#applicationClient()} when the subscriber starts. That
 * happens outside of any request, so the stream is opened for the application and not for
 * a caller: the server must accept it with the credentials of the application, or with
 * none.
 * <p>
 * The streams are opened when the subscriber is started, without waiting for the servers,
 * and a server that cannot be reached does not fail the start. A subscriber without
 * listeners opens no stream, so add the listeners before it is started.
 * <p>
 * A stream that ends, or that cannot be opened, is opened again for as long as the
 * subscriber runs: after one second at first, and after twice the previous pause each
 * time, up to the maximum reopen delay. The pauses start over with one second once a
 * stream has stayed up for the maximum reopen delay. So a short interruption is over
 * quickly, and neither a server that stays away nor one that accepts the stream only to
 * end it is asked more often than the maximum delay allows.
 * <p>
 * The listeners are told that changes may have been missed when a stream is established
 * and when an established stream ends, but not for a stream that could not be opened.
 * <p>
 * Register a {@link SyncModernMcpToolCallbackProvider} as a listener to have it drop a
 * kept tool listing as soon as the server reports that its tools changed. A stream always
 * listens for the changes of the tools, and for those of the prompts and of the list of
 * resources if the connection was added with them.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 * @see SyncModernMcpChangeListener
 */
public final class SyncModernMcpChangeSubscriber implements SmartLifecycle {

	private static final Log logger = LogFactory.getLog(SyncModernMcpChangeSubscriber.class);

	private static final Duration DEFAULT_MAX_REOPEN_DELAY = Duration.ofSeconds(30);

	private static final Duration DEFAULT_INITIAL_REOPEN_DELAY = Duration.ofSeconds(1);

	/** The streams and the listeners, shared with the asynchronous subscriber. */
	private final ModernChangeStreams<SyncModernMcpChangeListener> streams;

	private SyncModernMcpChangeSubscriber(List<ModernChangeStreams.Subscription> subscriptions,
			List<SyncModernMcpChangeListener> listeners, Duration initialReopenDelay, Duration maxReopenDelay,
			Supplier<ScheduledExecutorService> schedulerFactory) {
		this.streams = new ModernChangeStreams<>(logger, subscriptions, listeners, new Listeners(), initialReopenDelay,
				maxReopenDelay, schedulerFactory);
	}

	/**
	 * Creates a builder for a {@link SyncModernMcpChangeSubscriber}.
	 * @return the builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Adds a listener. The listeners are told in their order: one that is
	 * {@link org.springframework.core.Ordered} or annotated with
	 * {@link org.springframework.core.annotation.Order} by that order, and the others
	 * last, in the order they were added. A subscriber that was started without listeners
	 * has opened no stream, and it opens the streams when the first listener is added.
	 * @param listener the listener
	 */
	public void addListener(SyncModernMcpChangeListener listener) {
		this.streams.addListener(listener);
	}

	/**
	 * The names of the connections a listen stream is kept open to.
	 * @return the connection names
	 */
	public List<String> connectionNames() {
		return this.streams.connectionNames();
	}

	/**
	 * Opens the listen streams, without waiting for the servers.
	 */
	@Override
	public void start() {
		this.streams.start();
	}

	/**
	 * Closes the listen streams. They are not opened again until the next start.
	 */
	@Override
	public void stop() {
		this.streams.stop();
	}

	@Override
	public boolean isRunning() {
		return this.streams.isRunning();
	}

	/**
	 * Opens the listen stream of a connection with one client, which is taken from the
	 * connection when the stream is first opened.
	 */
	private static ModernChangeStreams.StreamOpener opener(SyncModernMcpConnection connection, boolean promptsChanged,
			boolean resourcesChanged) {
		AtomicReference<McpSyncClient> streamClient = new AtomicReference<>();
		return signals -> {
			McpSyncClient client = streamClient
				.updateAndGet(current -> current != null ? current : connection.applicationClient());
			McpSyncClient.ListenSpec listenSpec = client.listen().toolsChanged();
			if (promptsChanged) {
				listenSpec = listenSpec.promptsChanged();
			}
			if (resourcesChanged) {
				listenSpec = listenSpec.resourcesChanged();
			}
			McpSyncClient.Listening listening = listenSpec.open(new McpSyncSubscriptionListener() {

				@Override
				public void onEvent(SubscriptionEvent event) {
					signals.onEvent(event);
				}

				@Override
				public void onError(Throwable error) {
					signals.onError(error);
				}

				@Override
				public void onComplete() {
					signals.onComplete();
				}

			});
			return listening::close;
		};
	}

	/**
	 * Tells the listeners one after the other, on the thread of the stream.
	 */
	private static final class Listeners implements ModernChangeStreams.Dispatcher<SyncModernMcpChangeListener> {

		@Override
		public void changed(List<SyncModernMcpChangeListener> listeners, McpModernConnectionInfo connectionInfo,
				SubscriptionEvent event) {
			tell(listeners, connectionInfo, listener -> listener.changed(connectionInfo, event));
		}

		@Override
		public void established(List<SyncModernMcpChangeListener> listeners, McpModernConnectionInfo connectionInfo) {
			tell(listeners, connectionInfo, listener -> listener.streamEstablished(connectionInfo));
			tell(listeners, connectionInfo, listener -> listener.changesMayBeMissed(connectionInfo));
		}

		@Override
		public void ended(List<SyncModernMcpChangeListener> listeners, McpModernConnectionInfo connectionInfo) {
			tell(listeners, connectionInfo, listener -> listener.changesMayBeMissed(connectionInfo));
		}

		private static void tell(List<SyncModernMcpChangeListener> listeners, McpModernConnectionInfo connectionInfo,
				Consumer<SyncModernMcpChangeListener> call) {
			for (SyncModernMcpChangeListener listener : listeners) {
				try {
					call.accept(listener);
				}
				catch (RuntimeException ex) {
					// An exception that reaches the client would end the stream.
					logger.warn("A listener failed for the MCP connection '" + connectionInfo.name() + "'", ex);
				}
			}
		}

	}

	/**
	 * Builder for {@link SyncModernMcpChangeSubscriber}.
	 */
	public static final class Builder {

		private final List<ModernChangeStreams.Subscription> subscriptions = new ArrayList<>();

		private final List<SyncModernMcpChangeListener> listeners = new ArrayList<>();

		private Duration initialReopenDelay = DEFAULT_INITIAL_REOPEN_DELAY;

		private Duration maxReopenDelay = DEFAULT_MAX_REOPEN_DELAY;

		private Supplier<ScheduledExecutorService> schedulerFactory = () -> Executors
			.newSingleThreadScheduledExecutor(runnable -> {
				Thread thread = new Thread(runnable, "mcp-listen");
				thread.setDaemon(true);
				return thread;
			});

		private Builder() {
		}

		/**
		 * Adds a connection to keep a listen stream open to, for the changes of the tools
		 * of its server.
		 * @param connection the connection
		 * @return this builder
		 */
		public Builder connection(SyncModernMcpConnection connection) {
			return this.connection(connection, false, false);
		}

		/**
		 * Adds a connection to keep a listen stream open to, for the changes of the tools
		 * of its server and, if asked for, those of its prompts and of its list of
		 * resources.
		 * @param connection the connection
		 * @param promptsChanged whether to listen for the changes of the prompts as well
		 * @param resourcesChanged whether to listen for the changes of the list of
		 * resources as well
		 * @return this builder
		 */
		public Builder connection(SyncModernMcpConnection connection, boolean promptsChanged,
				boolean resourcesChanged) {
			Assert.notNull(connection, "connection must not be null");
			this.subscriptions.add(new ModernChangeStreams.Subscription(connection.info(),
					opener(connection, promptsChanged, resourcesChanged)));
			return this;
		}

		/**
		 * Adds a listener that hears about what the servers report.
		 * @param listener the listener
		 * @return this builder
		 */
		public Builder listener(SyncModernMcpChangeListener listener) {
			Assert.notNull(listener, "listener must not be null");
			this.listeners.add(listener);
			return this;
		}

		/**
		 * Sets the longest pause before a stream that ended, or that could not be opened,
		 * is opened again. The first pause is one second, or this delay if it is shorter,
		 * and each further one is twice the previous one, up to this delay. Defaults to
		 * 30 seconds.
		 * @param maxReopenDelay the longest pause, greater than zero
		 * @return this builder
		 */
		public Builder maxReopenDelay(Duration maxReopenDelay) {
			Assert.notNull(maxReopenDelay, "maxReopenDelay must not be null");
			Assert.isTrue(!maxReopenDelay.isNegative() && !maxReopenDelay.isZero(),
					"maxReopenDelay must be greater than zero");
			this.maxReopenDelay = maxReopenDelay;
			return this;
		}

		/**
		 * Sets the first pause before a stream is opened again. For tests.
		 */
		Builder initialReopenDelay(Duration initialReopenDelay) {
			Assert.isTrue(!initialReopenDelay.isNegative() && !initialReopenDelay.isZero(),
					"initialReopenDelay must be greater than zero");
			this.initialReopenDelay = initialReopenDelay;
			return this;
		}

		/**
		 * Sets the factory of the scheduler that opens the streams again. For tests.
		 */
		Builder schedulerFactory(Supplier<ScheduledExecutorService> schedulerFactory) {
			this.schedulerFactory = schedulerFactory;
			return this;
		}

		/**
		 * Builds the subscriber. It opens no stream before it is started.
		 * @return the subscriber
		 */
		public SyncModernMcpChangeSubscriber build() {
			return new SyncModernMcpChangeSubscriber(this.subscriptions, this.listeners, this.initialReopenDelay,
					this.maxReopenDelay, this.schedulerFactory);
		}

	}

}
