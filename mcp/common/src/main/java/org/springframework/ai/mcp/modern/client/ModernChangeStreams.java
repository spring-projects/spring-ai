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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.modelcontextprotocol.modern.McpSchema.SubscriptionFilter;
import io.modelcontextprotocol.modern.client.SubscriptionEvent;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;

import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.Assert;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * The listen streams of the modern connections of a change subscriber: when a stream is
 * opened, when it is opened again, and what the listeners are told. The synchronous and
 * the asynchronous subscriber share it, and differ in how a stream is opened and in how a
 * listener is told.
 *
 * @param <L> the type of the listeners
 * @author Christian Tzolov
 */
final class ModernChangeStreams<L> {

	private final Log logger;

	private final List<ListenStream> streams;

	/**
	 * The listeners in the order they are told, replaced as a whole when one is added.
	 */
	private volatile List<L> listeners = List.of();

	private final Dispatcher<L> dispatcher;

	/** The pauses before a stream is opened again: doubling, up to the maximum. */
	private final ExponentialBackOff reopenBackOff;

	private final Duration maxReopenDelay;

	private final Supplier<ScheduledExecutorService> schedulerFactory;

	private final Object lifecycleMonitor = new Object();

	private volatile @Nullable ScheduledExecutorService scheduler;

	private volatile boolean running;

	ModernChangeStreams(Log logger, List<Subscription> subscriptions, List<L> listeners, Dispatcher<L> dispatcher,
			Duration initialReopenDelay, Duration maxReopenDelay, Supplier<ScheduledExecutorService> schedulerFactory) {
		this.logger = logger;
		this.dispatcher = dispatcher;
		// The first pause is never longer than the longest one.
		Duration initial = initialReopenDelay.compareTo(maxReopenDelay) < 0 ? initialReopenDelay : maxReopenDelay;
		this.reopenBackOff = new ExponentialBackOff(initial.toMillis(), 2.0);
		this.reopenBackOff.setMaxInterval(maxReopenDelay.toMillis());
		this.maxReopenDelay = maxReopenDelay;
		this.schedulerFactory = schedulerFactory;
		// Created last: a stream starts with the backoff set above.
		this.streams = subscriptions.stream().map(ListenStream::new).toList();
		List<L> ordered = new ArrayList<>(listeners);
		AnnotationAwareOrderComparator.sort(ordered);
		this.listeners = List.copyOf(ordered);
	}

	/**
	 * Adds a listener. The listeners are told in their order: one that is
	 * {@link org.springframework.core.Ordered} or annotated with
	 * {@link org.springframework.core.annotation.Order} by that order, and the others
	 * last, in the order they were added. Streams that were not opened for lack of a
	 * listener are opened now.
	 * @param listener the listener
	 */
	void addListener(L listener) {
		Assert.notNull(listener, "listener must not be null");
		synchronized (this.lifecycleMonitor) {
			List<L> listeners = new ArrayList<>(this.listeners);
			listeners.add(listener);
			AnnotationAwareOrderComparator.sort(listeners);
			this.listeners = List.copyOf(listeners);
			this.openStreams();
		}
	}

	List<String> connectionNames() {
		return this.streams.stream().map(stream -> stream.connectionInfo.name()).toList();
	}

	/**
	 * Opens the listen streams, without waiting for the servers.
	 */
	void start() {
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
	void stop() {
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

	boolean isRunning() {
		return this.running;
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
	 * Opens the listen stream of one connection.
	 */
	@FunctionalInterface
	interface StreamOpener {

		/**
		 * Opens a stream, without waiting for the server. It is called with the monitor
		 * of the stream held, so never concurrently for one connection.
		 * @param signals receives what the stream reports
		 * @return closes the stream
		 */
		Runnable open(StreamSignals signals);

	}

	/**
	 * What a listen stream reports.
	 */
	interface StreamSignals {

		void onEvent(SubscriptionEvent event);

		void onError(Throwable error);

		void onComplete();

	}

	/**
	 * Tells the listeners, in their order. A listener that fails must not keep the others
	 * from being told, and its failure must not reach the stream, which it would end.
	 *
	 * @param <L> the type of the listeners
	 */
	interface Dispatcher<L> {

		/**
		 * The server of a connection reported a change.
		 */
		void changed(List<L> listeners, McpModernConnectionInfo connectionInfo, SubscriptionEvent event);

		/**
		 * A stream was established: the server can be reached, and what changed before is
		 * not known. All listeners hear that the stream is established before any of them
		 * hears that changes may have been missed.
		 */
		void established(List<L> listeners, McpModernConnectionInfo connectionInfo);

		/**
		 * An established stream ended, so changes may be missed from now on.
		 */
		void ended(List<L> listeners, McpModernConnectionInfo connectionInfo);

	}

	/**
	 * A connection and the way its listen stream is opened.
	 */
	record Subscription(McpModernConnectionInfo connectionInfo, StreamOpener opener) {
	}

	/**
	 * The listen stream of one connection. Its state is guarded by its monitor.
	 */
	private final class ListenStream {

		private final McpModernConnectionInfo connectionInfo;

		private final StreamOpener opener;

		/** Closes the stream that is open, or {@code null} if none is. */
		private @Nullable Runnable closer;

		/**
		 * Counts the streams opened so far. The events of a stream that was closed or
		 * replaced carry an older number and are ignored.
		 */
		private long opened;

		/** The number of the last stream that ended by itself. */
		private long ended;

		/** When the current stream was established, or zero if it is not. */
		private long establishedAtNanos;

		/**
		 * How often a stream ended, or could not be opened, since one last stayed up for
		 * the maximum reopen delay.
		 */
		private int failures;

		private BackOffExecution reopenPauses = ModernChangeStreams.this.reopenBackOff.start();

		private boolean missingReported;

		private ListenStream(Subscription subscription) {
			this.connectionInfo = subscription.connectionInfo();
			this.opener = subscription.opener();
		}

		private void open() {
			RuntimeException failure;
			long pauseMillis;
			int failures;
			synchronized (this) {
				if (!ModernChangeStreams.this.running || this.closer != null) {
					return;
				}
				long stream = ++this.opened;
				try {
					Runnable closer = this.opener.open(new Signals(stream));
					// A stream may end while it is being opened, on this very thread.
					// It is then over, and the next one is already scheduled.
					if (this.ended != stream) {
						this.closer = closer;
					}
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
			ModernChangeStreams.this.openAgain(this, pauseMillis);
		}

		private synchronized void close() {
			// Whatever the closed stream still reports is ignored.
			this.opened++;
			this.establishedAtNanos = 0;
			if (this.closer != null) {
				this.closer.run();
				this.closer = null;
			}
		}

		private void changed(long stream, SubscriptionEvent event) {
			synchronized (this) {
				if (stream != this.opened) {
					return;
				}
			}
			ModernChangeStreams.this.dispatcher.changed(ModernChangeStreams.this.listeners, this.connectionInfo, event);
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
			// What changed before the stream was established is not known.
			ModernChangeStreams.this.dispatcher.established(ModernChangeStreams.this.listeners, this.connectionInfo);
		}

		private void ended(long stream, @Nullable Throwable error) {
			boolean wasEstablished;
			long pauseMillis;
			int failures;
			synchronized (this) {
				if (stream != this.opened) {
					return;
				}
				this.ended = stream;
				this.closer = null;
				wasEstablished = this.establishedAtNanos != 0;
				if (wasEstablished && System.nanoTime()
						- this.establishedAtNanos >= ModernChangeStreams.this.maxReopenDelay.toNanos()) {
					// The stream stayed up, so this is a new interruption: the pauses
					// start over.
					this.failures = 0;
					this.reopenPauses = ModernChangeStreams.this.reopenBackOff.start();
				}
				this.establishedAtNanos = 0;
				failures = ++this.failures;
				pauseMillis = this.reopenPauses.nextBackOff();
			}
			if (!ModernChangeStreams.this.running) {
				return;
			}
			String message = "The listen stream of the MCP connection '" + this.connectionInfo.name() + "' "
					+ (wasEstablished ? "ended" : "could not be established") + "; " + this.retryNote();
			this.log(failures, error != null ? message + ": " + error : message);
			if (wasEstablished) {
				// Not for a stream that never reported anything: it changes nothing
				// about what is kept.
				ModernChangeStreams.this.dispatcher.ended(ModernChangeStreams.this.listeners, this.connectionInfo);
			}
			ModernChangeStreams.this.openAgain(this, pauseMillis);
		}

		private String retryNote() {
			return "it is opened again until the server accepts it, with pauses that grow up to "
					+ ModernChangeStreams.this.maxReopenDelay.toSeconds() + " seconds";
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
		private final class Signals implements StreamSignals {

			private final long stream;

			private Signals(long stream) {
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

}
