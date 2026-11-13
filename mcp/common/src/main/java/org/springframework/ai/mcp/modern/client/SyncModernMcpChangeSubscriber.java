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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.modelcontextprotocol.modern.McpSchema.SubscriptionFilter;
import io.modelcontextprotocol.modern.client.McpSyncClient;
import io.modelcontextprotocol.modern.client.McpSyncSubscriptionListener;
import io.modelcontextprotocol.modern.client.SubscriptionEvent;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.context.SmartLifecycle;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.Assert;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Keeps a listen stream open to each of its modern MCP connections and tells its
 * {@link SyncModernMcpChangeListener listeners} what the servers report.
 * <p>
 * A listen stream is a long-lived request, so it is not opened by the client of a chat
 * request. Each connection gets one client for its stream, taken from
 * {@link SyncModernMcpConnection#client()} when the subscriber starts. That happens
 * outside of any request, so the stream is opened for the application and not for a
 * caller: the server must accept it with the credentials of the application, or with
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

	private final List<ListenStream> streams;

	/**
	 * The listeners in the order they are told, replaced as a whole when one is added.
	 */
	private volatile List<SyncModernMcpChangeListener> listeners = List.of();

	/** The pauses before a stream is opened again: doubling, up to the maximum. */
	private final ExponentialBackOff reopenBackOff;

	private final Duration maxReopenDelay;

	private final Supplier<ScheduledExecutorService> schedulerFactory;

	private final Object lifecycleMonitor = new Object();

	private volatile @Nullable ScheduledExecutorService scheduler;

	private volatile boolean running;

	private SyncModernMcpChangeSubscriber(List<Subscription> subscriptions, List<SyncModernMcpChangeListener> listeners,
			Duration initialReopenDelay, Duration maxReopenDelay, Supplier<ScheduledExecutorService> schedulerFactory) {
		// The first pause is never longer than the longest one.
		Duration initial = initialReopenDelay.compareTo(maxReopenDelay) < 0 ? initialReopenDelay : maxReopenDelay;
		this.reopenBackOff = new ExponentialBackOff(initial.toMillis(), 2.0);
		this.reopenBackOff.setMaxInterval(maxReopenDelay.toMillis());
		this.maxReopenDelay = maxReopenDelay;
		this.schedulerFactory = schedulerFactory;
		// Created last: a stream starts with the backoff set above.
		this.streams = subscriptions.stream().map(ListenStream::new).toList();
		List<SyncModernMcpChangeListener> ordered = new ArrayList<>(listeners);
		AnnotationAwareOrderComparator.sort(ordered);
		this.listeners = List.copyOf(ordered);
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
		Assert.notNull(listener, "listener must not be null");
		synchronized (this.lifecycleMonitor) {
			List<SyncModernMcpChangeListener> listeners = new ArrayList<>(this.listeners);
			listeners.add(listener);
			AnnotationAwareOrderComparator.sort(listeners);
			this.listeners = List.copyOf(listeners);
			this.openStreams();
		}
	}

	/**
	 * The names of the connections a listen stream is kept open to.
	 * @return the connection names
	 */
	public List<String> connectionNames() {
		return this.streams.stream().map(stream -> stream.connectionInfo.name()).toList();
	}

	/**
	 * Opens the listen streams, without waiting for the servers.
	 */
	@Override
	public void start() {
		synchronized (this.lifecycleMonitor) {
			if (this.running) {
				return;
			}
			this.running = true;
			if (!this.streams.isEmpty() && this.listeners.isEmpty()) {
				logger.warn("No listen stream is opened to the MCP connections " + this.connectionNames()
						+ " yet, because nothing listens for the changes of their servers; the streams are opened "
						+ "when a listener is added");
			}
			this.openStreams();
		}
	}

	/**
	 * Opens the streams of a running subscriber that has a listener, unless they are open
	 * already. Called with the lifecycle monitor held.
	 */
	private void openStreams() {
		if (!this.running || this.scheduler != null || this.streams.isEmpty() || this.listeners.isEmpty()) {
			return;
		}
		this.scheduler = this.schedulerFactory.get();
		this.streams.forEach(ListenStream::open);
	}

	/**
	 * Closes the listen streams. They are not opened again until the next start.
	 */
	@Override
	public void stop() {
		synchronized (this.lifecycleMonitor) {
			if (!this.running) {
				return;
			}
			this.running = false;
			ScheduledExecutorService scheduler = this.scheduler;
			if (scheduler != null) {
				scheduler.shutdownNow();
				this.scheduler = null;
			}
			this.streams.forEach(ListenStream::close);
		}
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}

	private void tellListeners(McpModernConnectionInfo connectionInfo, Consumer<SyncModernMcpChangeListener> call) {
		for (SyncModernMcpChangeListener listener : this.listeners) {
			try {
				call.accept(listener);
			}
			catch (RuntimeException ex) {
				// An exception that reaches the client would end the stream.
				logger.warn("A listener failed for the MCP connection '" + connectionInfo.name() + "'", ex);
			}
		}
	}

	private void openAgain(ListenStream stream, long pauseMillis) {
		ScheduledExecutorService scheduler = this.scheduler;
		if (scheduler == null || !this.running) {
			return;
		}
		try {
			scheduler.schedule(stream::open, pauseMillis, TimeUnit.MILLISECONDS);
		}
		catch (RejectedExecutionException ex) {
			// The subscriber was stopped in the meantime.
			logger.debug("The listen stream is not opened again", ex);
		}
	}

	/**
	 * The listen stream of one connection. Its state is guarded by its monitor; the
	 * listeners are never called while the monitor is held.
	 */
	private final class ListenStream {

		private final SyncModernMcpConnection connection;

		private final McpModernConnectionInfo connectionInfo;

		/**
		 * The client of the stream, taken from the connection when it is first opened.
		 */
		private @Nullable McpSyncClient client;

		private McpSyncClient.@Nullable Listening listening;

		/**
		 * Counts the streams opened so far. The events of a stream that was closed or
		 * replaced carry an older number and are ignored.
		 */
		private long opened;

		/** When the current stream was established, or zero if it is not. */
		private long establishedAtNanos;

		/**
		 * How often a stream ended, or could not be opened, since one last stayed up for
		 * the maximum reopen delay.
		 */
		private int failures;

		private BackOffExecution reopenPauses = SyncModernMcpChangeSubscriber.this.reopenBackOff.start();

		private boolean missingReported;

		private final boolean promptsChanged;

		private final boolean resourcesChanged;

		private ListenStream(Subscription subscription) {
			this.connection = subscription.connection();
			this.connectionInfo = subscription.connection().info();
			this.promptsChanged = subscription.promptsChanged();
			this.resourcesChanged = subscription.resourcesChanged();
		}

		private void open() {
			RuntimeException failure;
			long pauseMillis;
			int failures;
			synchronized (this) {
				if (!SyncModernMcpChangeSubscriber.this.running || this.listening != null) {
					return;
				}
				long stream = ++this.opened;
				try {
					if (this.client == null) {
						this.client = this.connection.client();
					}
					McpSyncClient.ListenSpec listenSpec = this.client.listen().toolsChanged();
					if (this.promptsChanged) {
						listenSpec = listenSpec.promptsChanged();
					}
					if (this.resourcesChanged) {
						listenSpec = listenSpec.resourcesChanged();
					}
					this.listening = listenSpec.open(new StreamListener(stream));
					return;
				}
				catch (RuntimeException ex) {
					failure = ex;
					failures = ++this.failures;
					pauseMillis = this.reopenPauses.nextBackOff();
				}
			}
			// The stream was not up, so there is nothing to tell the listeners.
			this.log(failures, "The listen stream of the MCP connection '" + this.connectionInfo.name()
					+ "' could not be opened; " + this.retryNote() + ": " + failure);
			SyncModernMcpChangeSubscriber.this.openAgain(this, pauseMillis);
		}

		private synchronized void close() {
			// Whatever the closed stream still reports is ignored.
			this.opened++;
			this.establishedAtNanos = 0;
			if (this.listening != null) {
				this.listening.close();
				this.listening = null;
			}
		}

		private void changed(long stream, SubscriptionEvent event) {
			synchronized (this) {
				if (stream != this.opened) {
					return;
				}
			}
			SyncModernMcpChangeSubscriber.this.tellListeners(this.connectionInfo,
					listener -> listener.changed(this.connectionInfo, event));
		}

		private void established(long stream, SubscriptionEvent.Subscribed subscribed) {
			int failuresBefore;
			boolean reportMissing;
			synchronized (this) {
				if (stream != this.opened) {
					return;
				}
				this.establishedAtNanos = System.nanoTime();
				failuresBefore = this.failures;
				reportMissing = !subscribed.missing().isEmpty() && !this.missingReported;
				this.missingReported = this.missingReported || reportMissing;
			}
			if (failuresBefore > 0) {
				this.log(failuresBefore,
						"Listening for the changes of the MCP connection '" + this.connectionInfo.name() + "' again",
						true);
			}
			if (reportMissing) {
				SubscriptionFilter missing = subscribed.missing();
				logger.warn("The MCP server of the connection '" + this.connectionInfo.name()
						+ "' does not report all the changes that were asked for; not reported: " + missing);
			}
			SyncModernMcpChangeSubscriber.this.tellListeners(this.connectionInfo,
					listener -> listener.streamEstablished(this.connectionInfo));
			// What changed before the stream was established is not known.
			SyncModernMcpChangeSubscriber.this.tellListeners(this.connectionInfo,
					listener -> listener.changesMayBeMissed(this.connectionInfo));
		}

		private void ended(long stream, @Nullable Throwable error) {
			boolean wasEstablished;
			long pauseMillis;
			int failures;
			synchronized (this) {
				if (stream != this.opened) {
					return;
				}
				this.listening = null;
				wasEstablished = this.establishedAtNanos != 0;
				if (wasEstablished && System.nanoTime()
						- this.establishedAtNanos >= SyncModernMcpChangeSubscriber.this.maxReopenDelay.toNanos()) {
					// The stream stayed up, so this is a new interruption: the pauses
					// start over.
					this.failures = 0;
					this.reopenPauses = SyncModernMcpChangeSubscriber.this.reopenBackOff.start();
				}
				this.establishedAtNanos = 0;
				failures = ++this.failures;
				pauseMillis = this.reopenPauses.nextBackOff();
			}
			if (!SyncModernMcpChangeSubscriber.this.running) {
				return;
			}
			String message = "The listen stream of the MCP connection '" + this.connectionInfo.name() + "' "
					+ (wasEstablished ? "ended" : "could not be established") + "; " + this.retryNote();
			this.log(failures, error != null ? message + ": " + error : message);
			if (wasEstablished) {
				// Not for a stream that never reported anything: it changes nothing
				// about what is kept.
				SyncModernMcpChangeSubscriber.this.tellListeners(this.connectionInfo,
						listener -> listener.changesMayBeMissed(this.connectionInfo));
			}
			SyncModernMcpChangeSubscriber.this.openAgain(this, pauseMillis);
		}

		private String retryNote() {
			return "it is opened again until the server accepts it, with pauses that grow up to "
					+ SyncModernMcpChangeSubscriber.this.maxReopenDelay.toSeconds() + " seconds";
		}

		private void log(int failures, String message) {
			this.log(failures, message, false);
		}

		/**
		 * Reports the first failure of an interruption, and the recovery from it, and
		 * leaves the further ones to the debug level.
		 */
		private void log(int failures, String message, boolean recovery) {
			// The end of an outage is always reported, like its first failure. Only the
			// failures in between are not.
			if (recovery) {
				logger.info(message);
			}
			else if (failures > 1) {
				logger.debug(message);
			}
			else {
				logger.warn(message);
			}
		}

		/**
		 * Receives the events of one stream, with the number of that stream.
		 */
		private final class StreamListener implements McpSyncSubscriptionListener {

			private final long stream;

			private StreamListener(long stream) {
				this.stream = stream;
			}

			@Override
			public void onEvent(SubscriptionEvent event) {
				if (event instanceof SubscriptionEvent.Subscribed subscribed) {
					ListenStream.this.established(this.stream, subscribed);
				}
				else {
					ListenStream.this.changed(this.stream, event);
				}
			}

			@Override
			public void onError(Throwable error) {
				ListenStream.this.ended(this.stream, error);
			}

			@Override
			public void onComplete() {
				ListenStream.this.ended(this.stream, null);
			}

		}

	}

	/**
	 * Builder for {@link SyncModernMcpChangeSubscriber}.
	 */
	public static final class Builder {

		private final List<Subscription> subscriptions = new ArrayList<>();

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
			this.subscriptions.add(new Subscription(connection, promptsChanged, resourcesChanged));
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

	/**
	 * A connection and what its listen stream listens for, next to the tools.
	 */
	private record Subscription(SyncModernMcpConnection connection, boolean promptsChanged, boolean resourcesChanged) {
	}

}
